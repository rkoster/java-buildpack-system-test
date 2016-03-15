/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.cloudfoundry.test;

import org.cloudfoundry.client.CloudFoundryClient;
import org.cloudfoundry.client.v2.organizations.ListOrganizationsRequest;
import org.cloudfoundry.client.v2.spaces.ListSpacesRequest;
import org.cloudfoundry.operations.CloudFoundryOperations;
import org.cloudfoundry.operations.CloudFoundryOperationsBuilder;
import org.cloudfoundry.spring.client.SpringCloudFoundryClient;
import org.cloudfoundry.util.PaginationUtils;
import org.cloudfoundry.util.ResourceUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.AsyncRestTemplate;
import reactor.core.publisher.Mono;

@ComponentScan
@Configuration
@EnableAutoConfiguration
public class IntegrationTestConfiguration {

    @Bean
    String buildpack(@Value("${test.buildpack}") String buildpack) {
        return buildpack;
    }

    @Bean
    SpringCloudFoundryClient cloudFoundryClient(@Value("${test.host}") String host,
                                                @Value("${test.username}") String username,
                                                @Value("${test.password}") String password,
                                                @Value("${test.skipSslValidation:false}") Boolean skipSslValidation) {

        return SpringCloudFoundryClient.builder()
            .host(host)
            .username(username)
            .password(password)
            .skipSslValidation(skipSslValidation)
            .build();
    }

    @Bean
    CloudFoundryOperations cloudFoundryOperations(CloudFoundryClient cloudFoundryClient,
                                                  @Value("${test.organization}") String organization,
                                                  @Value("${test.space}") String space) {
        return new CloudFoundryOperationsBuilder()
            .cloudFoundryClient(cloudFoundryClient)
            .target(organization, space)
            .build();
    }

    @Bean
    Mono<String> organizationId(CloudFoundryClient cloudFoundryClient, @Value("${test.organization}") String organization) {
        return PaginationUtils
            .requestResources(page -> cloudFoundryClient.organizations()
                .list(ListOrganizationsRequest.builder()
                    .name(organization)
                    .page(page)
                    .build()))
            .single()
            .map(ResourceUtils::getId)
            .cache();
    }

    @Bean
    AsyncRestTemplate restOperations() {
        return new AsyncRestTemplate();
    }

    @Bean
    Mono<String> spaceId(CloudFoundryClient cloudFoundryClient, Mono<String> organizationId, @Value("${test.space}") String space) {
        return organizationId
            .flatMap(organizationId2 -> PaginationUtils
                .requestResources(page -> cloudFoundryClient.spaces()
                    .list(ListSpacesRequest.builder()
                        .name(space)
                        .organizationId(organizationId2)
                        .page(page)
                        .build())))
            .single()
            .map(ResourceUtils::getId)
            .cache();
    }

}

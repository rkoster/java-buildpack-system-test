/*
 * Copyright 2013-2016 the original author or authors.
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

package org.cloudfoundry.test.support;

import org.cloudfoundry.client.CloudFoundryClient;
import org.cloudfoundry.client.v2.Resource;
import org.cloudfoundry.client.v2.serviceinstances.CreateServiceInstanceRequest;
import org.cloudfoundry.client.v2.serviceinstances.CreateServiceInstanceResponse;
import org.cloudfoundry.client.v2.serviceinstances.ListServiceInstancesRequest;
import org.cloudfoundry.client.v2.serviceinstances.ServiceInstanceEntity;
import org.cloudfoundry.client.v2.serviceinstances.ServiceInstanceResource;
import org.cloudfoundry.client.v2.serviceplans.ListServicePlansRequest;
import org.cloudfoundry.client.v2.serviceplans.ServicePlanResource;
import org.cloudfoundry.client.v2.services.ListServicesRequest;
import org.cloudfoundry.client.v2.services.ServiceResource;
import org.cloudfoundry.util.OperationUtils;
import org.cloudfoundry.util.PaginationUtils;
import org.cloudfoundry.util.ResourceUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public final class DefaultServiceInstance implements ServiceInstance {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final CloudFoundryClient cloudFoundryClient;

    private final String name;

    private final String plan;

    private final String service;

    private final Mono<String> spaceId;

    DefaultServiceInstance(CloudFoundryClient cloudFoundryClient, String name, String service, String plan, Mono<String> spaceId) {
        this.cloudFoundryClient = cloudFoundryClient;
        this.name = name;
        this.plan = plan;
        this.service = service;
        this.spaceId = spaceId;
    }

    @Override
    public Mono<String> getName() {
        return this.spaceId
            .then(spaceId -> requestListServiceInstances(this.cloudFoundryClient, this.name, spaceId)
                .singleOrEmpty()
                .otherwiseIfEmpty(createService(this.cloudFoundryClient, this.name, this.plan, this.service, spaceId)
                    .doOnSubscribe(s -> this.logger.info("Creating {} ({}/{})", this.name, this.service, this.plan))))
            .doOnError(Throwable::printStackTrace)
            .after(() -> Mono.just(this.name))
            .cache();
    }

    // TODO: Replace with https://www.pivotaltracker.com/story/show/106155480
    private static Mono<Resource<ServiceInstanceEntity>> createService(CloudFoundryClient cloudFoundryClient, String name, String plan, String service, String spaceId) {
        return getServiceId(cloudFoundryClient, service)
            .then(serviceId -> getServicePlanId(cloudFoundryClient, serviceId, plan))
            .then(planId -> requestCreateServiceInstance(cloudFoundryClient, name, planId, spaceId));
    }

    private static Mono<String> getServiceId(CloudFoundryClient cloudFoundryClient, String service) {
        return requestListServices(cloudFoundryClient, service)
            .single()
            .map(ResourceUtils::getId);
    }

    private static Mono<String> getServicePlanId(CloudFoundryClient cloudFoundryClient, String serviceId, String plan) {
        return requestListServicePlans(cloudFoundryClient, serviceId)
            .filter(resource -> ResourceUtils.getEntity(resource).getName().equalsIgnoreCase(plan))
            .single()
            .map(ResourceUtils::getId);
    }

    private static Mono<CreateServiceInstanceResponse> requestCreateServiceInstance(CloudFoundryClient cloudFoundryClient, String name, String servicePlanId, String spaceId) {
        return cloudFoundryClient.serviceInstances()
            .create(CreateServiceInstanceRequest.builder()
                .name(name)
                .servicePlanId(servicePlanId)
                .spaceId(spaceId)
                .build());
    }

    // TODO: Replace with https://www.pivotaltracker.com/story/show/106155476
    private static Flux<Resource<ServiceInstanceEntity>> requestListServiceInstances(CloudFoundryClient cloudFoundryClient, String name, String spaceId) {
        return PaginationUtils
            .requestResources(page -> cloudFoundryClient.serviceInstances()
                .list(ListServiceInstancesRequest.builder()
                    .name(name)
                    .page(page)
                    .spaceId(spaceId)
                    .build()))
            .map(OperationUtils.<ServiceInstanceResource, Resource<ServiceInstanceEntity>>cast());
    }

    private static Flux<ServicePlanResource> requestListServicePlans(CloudFoundryClient cloudFoundryClient, String serviceId) {
        return PaginationUtils
            .requestResources(page -> cloudFoundryClient.servicePlans()
                .list(ListServicePlansRequest.builder()
                    .serviceId(serviceId)
                    .page(page)
                    .build()));
    }

    private static Flux<ServiceResource> requestListServices(CloudFoundryClient cloudFoundryClient, String service) {
        return PaginationUtils
            .requestResources(page -> cloudFoundryClient.services()
                .list(ListServicesRequest.builder()
                    .label(service)
                    .page(page)
                    .build()));
    }

}

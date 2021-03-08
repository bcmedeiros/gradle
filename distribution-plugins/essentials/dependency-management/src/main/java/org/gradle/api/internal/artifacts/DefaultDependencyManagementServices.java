/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.artifacts;

import org.gradle.StartParameter;
import org.gradle.api.Describable;
import org.gradle.api.artifacts.ConfigurablePublishArtifact;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.dsl.ArtifactHandler;
import org.gradle.api.artifacts.dsl.ComponentMetadataHandler;
import org.gradle.api.artifacts.dsl.ComponentModuleMetadataHandler;
import org.gradle.api.artifacts.dsl.DependencyConstraintHandler;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.artifacts.dsl.DependencyLockingHandler;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.attributes.AttributesSchema;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.internal.CollectionCallbackActionDecorator;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.internal.DomainObjectContext;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.artifacts.component.ComponentIdentifierFactory;
import org.gradle.api.internal.artifacts.configurations.ConfigurationContainerInternal;
import org.gradle.api.internal.artifacts.configurations.DefaultConfigurationContainer;
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider;
import org.gradle.api.internal.artifacts.dsl.ComponentMetadataHandlerInternal;
import org.gradle.api.internal.artifacts.dsl.DefaultArtifactHandler;
import org.gradle.api.internal.artifacts.dsl.DefaultComponentMetadataHandler;
import org.gradle.api.internal.artifacts.dsl.DefaultComponentModuleMetadataHandler;
import org.gradle.api.internal.artifacts.dsl.DefaultRepositoryHandler;
import org.gradle.api.internal.artifacts.dsl.PublishArtifactNotationParserFactory;
import org.gradle.api.internal.artifacts.dsl.dependencies.DefaultDependencyConstraintHandler;
import org.gradle.api.internal.artifacts.dsl.dependencies.DefaultDependencyHandler;
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactory;
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyLockingProvider;
import org.gradle.api.internal.artifacts.dsl.dependencies.GradlePluginVariantsSupport;
import org.gradle.api.internal.artifacts.dsl.dependencies.PlatformSupport;
import org.gradle.api.internal.artifacts.dsl.dependencies.ProjectFinder;
import org.gradle.api.internal.artifacts.ivyservice.DefaultConfigurationResolver;
import org.gradle.api.internal.artifacts.ivyservice.ErrorHandlingConfigurationResolver;
import org.gradle.api.internal.artifacts.ivyservice.IvyContextManager;
import org.gradle.api.internal.artifacts.ivyservice.IvyContextualArtifactPublisher;
import org.gradle.api.internal.artifacts.ivyservice.ShortCircuitEmptyConfigurationResolver;
import org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution.DependencySubstitutionRules;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ResolveIvyFactory;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.GradleModuleMetadataParser;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.GradlePomModuleDescriptorParser;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelectorScheme;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.verification.DependencyVerificationOverride;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.FileStoreAndIndexProvider;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.LocalComponentMetadataBuilder;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.LocalConfigurationMetadataBuilder;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.AttributeContainerSerializer;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionDescriptorFactory;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.store.ResolutionResultsStoreFactory;
import org.gradle.api.internal.artifacts.mvnsettings.LocalMavenRepositoryLocator;
import org.gradle.api.internal.artifacts.query.ArtifactResolutionQueryFactory;
import org.gradle.api.internal.artifacts.query.DefaultArtifactResolutionQueryFactory;
import org.gradle.api.internal.artifacts.repositories.DefaultBaseRepositoryFactory;
import org.gradle.api.internal.artifacts.repositories.DefaultUrlArtifactRepository;
import org.gradle.api.internal.artifacts.repositories.ResolutionAwareRepository;
import org.gradle.api.internal.artifacts.repositories.metadata.IvyMutableModuleMetadataFactory;
import org.gradle.api.internal.artifacts.repositories.metadata.MavenMutableModuleMetadataFactory;
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransportFactory;
import org.gradle.api.internal.artifacts.transform.ArtifactTransformActionScheme;
import org.gradle.api.internal.artifacts.transform.ArtifactTransformListener;
import org.gradle.api.internal.artifacts.transform.ArtifactTransformParameterScheme;
import org.gradle.api.internal.artifacts.transform.ConsumerProvidedVariantFinder;
import org.gradle.api.internal.artifacts.transform.DefaultArtifactTransforms;
import org.gradle.api.internal.artifacts.transform.DefaultTransformationRegistrationFactory;
import org.gradle.api.internal.artifacts.transform.DefaultTransformedVariantFactory;
import org.gradle.api.internal.artifacts.transform.DefaultTransformerInvocationFactory;
import org.gradle.api.internal.artifacts.transform.DefaultVariantTransformRegistry;
import org.gradle.api.internal.artifacts.transform.ImmutableTransformationWorkspaceServices;
import org.gradle.api.internal.artifacts.transform.MutableTransformationWorkspaceServices;
import org.gradle.api.internal.artifacts.transform.TransformationRegistrationFactory;
import org.gradle.api.internal.artifacts.transform.TransformedVariantFactory;
import org.gradle.api.internal.artifacts.transform.TransformerInvocationFactory;
import org.gradle.api.internal.artifacts.type.ArtifactTypeRegistry;
import org.gradle.api.internal.artifacts.type.DefaultArtifactTypeRegistry;
import org.gradle.api.internal.attributes.AttributeDesugaring;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.attributes.DefaultAttributesSchema;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.internal.collections.DomainObjectCollectionFactory;
import org.gradle.api.internal.component.ComponentTypeRegistry;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.file.FileLookup;
import org.gradle.api.internal.file.FilePropertyFactory;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.model.NamedObjectInstantiator;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectStateRegistry;
import org.gradle.api.internal.provider.PropertyFactory;
import org.gradle.api.internal.tasks.TaskResolver;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.configuration.internal.UserCodeApplicationContext;
import org.gradle.initialization.internal.InternalBuildFinishedListener;
import org.gradle.initialization.ProjectAccessListener;
import org.gradle.internal.authentication.AuthenticationSchemeRegistry;
import org.gradle.internal.build.BuildState;
import org.gradle.internal.component.external.ivypublish.DefaultArtifactPublisher;
import org.gradle.internal.component.external.ivypublish.DefaultIvyModuleDescriptorWriter;
import org.gradle.internal.component.external.model.JavaEcosystemVariantDerivationStrategy;
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetadata;
import org.gradle.internal.component.model.ComponentAttributeMatcher;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.execution.ExecutionEngine;
import org.gradle.internal.execution.history.ExecutionHistoryStore;
import org.gradle.internal.fingerprint.FileCollectionFingerprinterRegistry;
import org.gradle.internal.hash.ChecksumService;
import org.gradle.internal.hash.ClassLoaderHierarchyHasher;
import org.gradle.internal.instantiation.InstantiatorFactory;
import org.gradle.internal.isolation.IsolatableFactory;
import org.gradle.internal.locking.DefaultDependencyLockingHandler;
import org.gradle.internal.locking.DefaultDependencyLockingProvider;
import org.gradle.internal.management.DependencyResolutionManagementInternal;
import org.gradle.internal.model.CalculatedValueContainerFactory;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.resolve.caching.ComponentMetadataRuleExecutor;
import org.gradle.internal.resolve.caching.ComponentMetadataSupplierRuleExecutor;
import org.gradle.internal.resource.local.FileResourceListener;
import org.gradle.internal.resource.local.FileResourceRepository;
import org.gradle.internal.resource.local.LocallyAvailableResourceFinder;
import org.gradle.internal.service.DefaultServiceRegistry;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.snapshot.ValueSnapshotter;
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.internal.vfs.FileSystemAccess;
import org.gradle.util.internal.SimpleMapInterner;
import org.gradle.vcs.internal.VcsMappingsStore;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class DefaultDependencyManagementServices implements DependencyManagementServices {

    private final ServiceRegistry parent;

    public DefaultDependencyManagementServices(ServiceRegistry parent) {
        this.parent = parent;
    }

    @Override
    public DependencyResolutionServices create(FileResolver resolver, FileCollectionFactory fileCollectionFactory, DependencyMetaDataProvider dependencyMetaDataProvider, ProjectFinder projectFinder, DomainObjectContext domainObjectContext) {
        DefaultServiceRegistry services = new DefaultServiceRegistry(parent);
        services.add(FileResolver.class, resolver);
        services.add(FileCollectionFactory.class, fileCollectionFactory);
        services.add(DependencyMetaDataProvider.class, dependencyMetaDataProvider);
        services.add(ProjectFinder.class, projectFinder);
        services.add(DomainObjectContext.class, domainObjectContext);
        services.addProvider(new ArtifactTransformResolutionGradleUserHomeServices());
        services.addProvider(new DependencyResolutionScopeServices(domainObjectContext));
        return services.get(DependencyResolutionServices.class);
    }

    @Override
    public void addDslServices(ServiceRegistration registration, DomainObjectContext domainObjectContext) {
        registration.addProvider(new DependencyResolutionScopeServices(domainObjectContext));
    }

    private static class ArtifactTransformResolutionGradleUserHomeServices {

        ArtifactTransformListener createArtifactTransformListener() {
            return new ArtifactTransformListener() {
                @Override
                public void beforeTransformerInvocation(Describable transformer, Describable subject) {
                }

                @Override
                public void afterTransformerInvocation(Describable transformer, Describable subject) {
                }
            };
        }
    }

    private static class DependencyResolutionScopeServices {

        private final DomainObjectContext domainObjectContext;

        public DependencyResolutionScopeServices(DomainObjectContext domainObjectContext) {
            this.domainObjectContext = domainObjectContext;
        }

        void configure(ServiceRegistration registration) {
            registration.add(DefaultTransformedVariantFactory.class);
        }

        AttributesSchemaInternal createConfigurationAttributesSchema(InstantiatorFactory instantiatorFactory, IsolatableFactory isolatableFactory, PlatformSupport platformSupport) {
            DefaultAttributesSchema attributesSchema = instantiatorFactory.decorateLenient().newInstance(DefaultAttributesSchema.class, new ComponentAttributeMatcher(), instantiatorFactory, isolatableFactory);
            platformSupport.configureSchema(attributesSchema);
            GradlePluginVariantsSupport.configureSchema(attributesSchema);
            return attributesSchema;
        }

        MutableTransformationWorkspaceServices createTransformerWorkspaceServices(ProjectLayout projectLayout, ExecutionHistoryStore executionHistoryStore) {
            return new MutableTransformationWorkspaceServices(projectLayout.getBuildDirectory().dir(".transforms"), executionHistoryStore);
        }

        TransformerInvocationFactory createTransformerInvocationFactory(
                ExecutionEngine executionEngine,
                FileSystemAccess fileSystemAccess,
                ImmutableTransformationWorkspaceServices transformationWorkspaceServices,
                ArtifactTransformListener artifactTransformListener,
                FileCollectionFactory fileCollectionFactory,
                ProjectStateRegistry projectStateRegistry,
                BuildOperationExecutor buildOperationExecutor
        ) {
            return new DefaultTransformerInvocationFactory(
                executionEngine,
                fileSystemAccess,
                artifactTransformListener,
                transformationWorkspaceServices,
                fileCollectionFactory,
                projectStateRegistry,
                buildOperationExecutor
            );
        }

        TransformationRegistrationFactory createTransformationRegistrationFactory(
                BuildOperationExecutor buildOperationExecutor,
                IsolatableFactory isolatableFactory,
                ClassLoaderHierarchyHasher classLoaderHierarchyHasher,
                TransformerInvocationFactory transformerInvocationFactory,
                ValueSnapshotter valueSnapshotter,
                DomainObjectContext domainObjectContext,
                ArtifactTransformParameterScheme parameterScheme,
                ArtifactTransformActionScheme actionScheme,
                FileCollectionFingerprinterRegistry fileCollectionFingerprinterRegistry,
                CalculatedValueContainerFactory calculatedValueContainerFactory,
                FileCollectionFactory fileCollectionFactory,
                FileLookup fileLookup,
                ServiceRegistry internalServices,
                DocumentationRegistry documentationRegistry
        ) {
            return new DefaultTransformationRegistrationFactory(
                    buildOperationExecutor,
                    isolatableFactory,
                    classLoaderHierarchyHasher,
                    transformerInvocationFactory,
                    valueSnapshotter,
                    fileCollectionFactory,
                    fileLookup,
                    fileCollectionFingerprinterRegistry,
                    calculatedValueContainerFactory,
                    domainObjectContext,
                    parameterScheme,
                    actionScheme,
                    internalServices,
                    documentationRegistry
            );
        }

        VariantTransformRegistry createArtifactTransformRegistry(InstantiatorFactory instantiatorFactory, ImmutableAttributesFactory attributesFactory, ServiceRegistry services, TransformationRegistrationFactory transformationRegistrationFactory, ArtifactTransformParameterScheme parameterScheme) {
            return new DefaultVariantTransformRegistry(instantiatorFactory, attributesFactory, services, transformationRegistrationFactory, parameterScheme.getInstantiationScheme());
        }

        DefaultUrlArtifactRepository.Factory createDefaultUrlArtifactRepositoryFactory(FileResolver fileResolver) {
            return new DefaultUrlArtifactRepository.Factory(fileResolver);
        }

        BaseRepositoryFactory createBaseRepositoryFactory(
                LocalMavenRepositoryLocator localMavenRepositoryLocator,
                FileResolver fileResolver,
                FileCollectionFactory fileCollectionFactory,
                RepositoryTransportFactory repositoryTransportFactory,
                LocallyAvailableResourceFinder<ModuleComponentArtifactMetadata> locallyAvailableResourceFinder,
                FileStoreAndIndexProvider fileStoreAndIndexProvider,
                VersionSelectorScheme versionSelectorScheme,
                AuthenticationSchemeRegistry authenticationSchemeRegistry,
                IvyContextManager ivyContextManager,
                ImmutableAttributesFactory attributesFactory,
                ImmutableModuleIdentifierFactory moduleIdentifierFactory,
                InstantiatorFactory instantiatorFactory,
                FileResourceRepository fileResourceRepository,
                MavenMutableModuleMetadataFactory metadataFactory,
                IvyMutableModuleMetadataFactory ivyMetadataFactory,
                IsolatableFactory isolatableFactory,
                ObjectFactory objectFactory,
                CollectionCallbackActionDecorator callbackDecorator,
                NamedObjectInstantiator instantiator,
                DefaultUrlArtifactRepository.Factory urlArtifactRepositoryFactory,
                ChecksumService checksumService,
                ProviderFactory providerFactory
        ) {
            return new DefaultBaseRepositoryFactory(
                    localMavenRepositoryLocator,
                    fileResolver,
                    fileCollectionFactory,
                    repositoryTransportFactory,
                    locallyAvailableResourceFinder,
                    fileStoreAndIndexProvider.getArtifactIdentifierFileStore(),
                    fileStoreAndIndexProvider.getExternalResourceFileStore(),
                    new GradlePomModuleDescriptorParser(versionSelectorScheme, moduleIdentifierFactory, fileResourceRepository, metadataFactory),
                    new GradleModuleMetadataParser(attributesFactory, moduleIdentifierFactory, instantiator),
                    authenticationSchemeRegistry,
                    ivyContextManager,
                    moduleIdentifierFactory,
                    instantiatorFactory,
                    fileResourceRepository,
                    metadataFactory,
                    ivyMetadataFactory,
                    isolatableFactory,
                    objectFactory,
                    callbackDecorator,
                    urlArtifactRepositoryFactory,
                    checksumService,
                    providerFactory
            );
        }

        RepositoryHandler createRepositoryHandler(Instantiator instantiator, BaseRepositoryFactory baseRepositoryFactory, CollectionCallbackActionDecorator callbackDecorator) {
            return instantiator.newInstance(DefaultRepositoryHandler.class, baseRepositoryFactory, instantiator, callbackDecorator);
        }

        ConfigurationContainerInternal createConfigurationContainer(Instantiator instantiator,
                                                                    ConfigurationResolver configurationResolver, DomainObjectContext domainObjectContext,
                                                                    ListenerManager listenerManager, DependencyMetaDataProvider metaDataProvider,
                                                                    LocalComponentMetadataBuilder metaDataBuilder, FileCollectionFactory fileCollectionFactory,
                                                                    GlobalDependencyResolutionRules globalDependencyResolutionRules, VcsMappingsStore vcsMappingsStore, ComponentIdentifierFactory componentIdentifierFactory,
                                                                    BuildOperationExecutor buildOperationExecutor, ImmutableAttributesFactory attributesFactory,
                                                                    ImmutableModuleIdentifierFactory moduleIdentifierFactory, ComponentSelectorConverter componentSelectorConverter,
                                                                    DependencyLockingProvider dependencyLockingProvider,
                                                                    ProjectStateRegistry projectStateRegistry,
                                                                    CalculatedValueContainerFactory calculatedValueContainerFactory,
                                                                    ProjectAccessListener projectAccessListener,
                                                                    DocumentationRegistry documentationRegistry,
                                                                    CollectionCallbackActionDecorator callbackDecorator,
                                                                    UserCodeApplicationContext userCodeApplicationContext,
                                                                    DomainObjectCollectionFactory domainObjectCollectionFactory,
                                                                    NotationParser<Object, ComponentSelector> moduleSelectorNotationParser,
                                                                    ObjectFactory objectFactory) {
            return instantiator.newInstance(DefaultConfigurationContainer.class,
                    configurationResolver,
                    instantiator,
                    domainObjectContext,
                    listenerManager,
                    metaDataProvider,
                    projectAccessListener,
                    metaDataBuilder,
                    fileCollectionFactory,
                    globalDependencyResolutionRules.getDependencySubstitutionRules(),
                    vcsMappingsStore,
                    componentIdentifierFactory,
                    buildOperationExecutor,
                    taskResolverFor(domainObjectContext),
                    attributesFactory,
                    moduleIdentifierFactory,
                    componentSelectorConverter,
                    dependencyLockingProvider,
                    projectStateRegistry,
                    calculatedValueContainerFactory,
                    documentationRegistry,
                    callbackDecorator,
                    userCodeApplicationContext,
                    domainObjectCollectionFactory,
                    moduleSelectorNotationParser,
                    objectFactory
            );
        }

        @Nullable
        private TaskResolver taskResolverFor(DomainObjectContext domainObjectContext) {
            if (domainObjectContext instanceof ProjectInternal) {
                return ((ProjectInternal) domainObjectContext).getTasks();
            }
            return null;
        }

        ArtifactTypeRegistry createArtifactTypeRegistry(Instantiator instantiator, ImmutableAttributesFactory immutableAttributesFactory, CollectionCallbackActionDecorator decorator, VariantTransformRegistry transformRegistry) {
            return new DefaultArtifactTypeRegistry(instantiator, immutableAttributesFactory, decorator, transformRegistry);
        }

        DependencyHandler createDependencyHandler(Instantiator instantiator,
                                                  ConfigurationContainerInternal configurationContainer,
                                                  DependencyFactory dependencyFactory,
                                                  ProjectFinder projectFinder,
                                                  DependencyConstraintHandler dependencyConstraintHandler,
                                                  ComponentMetadataHandler componentMetadataHandler,
                                                  ComponentModuleMetadataHandler componentModuleMetadataHandler,
                                                  ArtifactResolutionQueryFactory resolutionQueryFactory,
                                                  AttributesSchema attributesSchema,
                                                  VariantTransformRegistry artifactTransformRegistrations,
                                                  ArtifactTypeRegistry artifactTypeRegistry,
                                                  ObjectFactory objects,
                                                  PlatformSupport platformSupport) {
            return instantiator.newInstance(DefaultDependencyHandler.class,
                configurationContainer,
                dependencyFactory,
                projectFinder,
                dependencyConstraintHandler,
                componentMetadataHandler,
                componentModuleMetadataHandler,
                resolutionQueryFactory,
                attributesSchema,
                artifactTransformRegistrations,
                artifactTypeRegistry,
                objects,
                platformSupport);
        }

        DependencyLockingHandler createDependencyLockingHandler(Instantiator instantiator, ConfigurationContainerInternal configurationContainer, DependencyLockingProvider dependencyLockingProvider) {
            // The lambda factory is to avoid eager creation of the configuration container
            return instantiator.newInstance(DefaultDependencyLockingHandler.class, (Supplier<ConfigurationContainerInternal>) () -> configurationContainer, dependencyLockingProvider);
        }

        DependencyLockingProvider createDependencyLockingProvider(FileResolver fileResolver, StartParameter startParameter, DomainObjectContext context, GlobalDependencyResolutionRules globalDependencyResolutionRules, ListenerManager listenerManager, PropertyFactory propertyFactory, FilePropertyFactory filePropertyFactory) {
            DefaultDependencyLockingProvider dependencyLockingProvider = new DefaultDependencyLockingProvider(fileResolver, startParameter, context, globalDependencyResolutionRules.getDependencySubstitutionRules(), propertyFactory, filePropertyFactory, listenerManager.getBroadcaster(FileResourceListener.class));
            if (startParameter.isWriteDependencyLocks()) {
                listenerManager.addListener(new InternalBuildFinishedListener() {
                    @Override
                    public void buildFinished(GradleInternal gradle, boolean failed) {
                        if (!failed) {
                            dependencyLockingProvider.buildFinished();
                        }
                    }
                });
            }
            return dependencyLockingProvider;
        }

        DependencyConstraintHandler createDependencyConstraintHandler(Instantiator instantiator, ConfigurationContainerInternal configurationContainer, DependencyFactory dependencyFactory, ObjectFactory objects, PlatformSupport platformSupport) {
            return instantiator.newInstance(DefaultDependencyConstraintHandler.class, configurationContainer, dependencyFactory, objects, platformSupport);
        }

        DefaultComponentMetadataHandler createComponentMetadataHandler(Instantiator instantiator,
                                                                       ImmutableModuleIdentifierFactory moduleIdentifierFactory,
                                                                       SimpleMapInterner interner,
                                                                       ImmutableAttributesFactory attributesFactory,
                                                                       IsolatableFactory isolatableFactory,
                                                                       ComponentMetadataRuleExecutor componentMetadataRuleExecutor,
                                                                       PlatformSupport platformSupport) {
            DefaultComponentMetadataHandler componentMetadataHandler = instantiator.newInstance(DefaultComponentMetadataHandler.class, instantiator, moduleIdentifierFactory, interner, attributesFactory, isolatableFactory, componentMetadataRuleExecutor, platformSupport);
            if (domainObjectContext.isScript()) {
                componentMetadataHandler.setVariantDerivationStrategy(JavaEcosystemVariantDerivationStrategy.getInstance());
            }
            return componentMetadataHandler;
        }

        DefaultComponentModuleMetadataHandler createComponentModuleMetadataHandler(Instantiator instantiator, ImmutableModuleIdentifierFactory moduleIdentifierFactory) {
            return instantiator.newInstance(DefaultComponentModuleMetadataHandler.class, moduleIdentifierFactory);
        }

        ArtifactHandler createArtifactHandler(Instantiator instantiator, DependencyMetaDataProvider dependencyMetaDataProvider, ConfigurationContainerInternal configurationContainer, DomainObjectContext context) {
            NotationParser<Object, ConfigurablePublishArtifact> publishArtifactNotationParser = new PublishArtifactNotationParserFactory(instantiator, dependencyMetaDataProvider, taskResolverFor(context)).create();
            return instantiator.newInstance(DefaultArtifactHandler.class, configurationContainer, publishArtifactNotationParser);
        }

        ComponentMetadataProcessorFactory createComponentMetadataProcessorFactory(ComponentMetadataHandlerInternal componentMetadataHandler, DependencyResolutionManagementInternal dependencyResolutionManagement, DomainObjectContext context) {
            if (context.isScript()) {
                return componentMetadataHandler::createComponentMetadataProcessor;
            }
            return componentMetadataHandler.createFactory(dependencyResolutionManagement);
        }

        GlobalDependencyResolutionRules createModuleMetadataHandler(ComponentMetadataProcessorFactory componentMetadataProcessorFactory, ComponentModuleMetadataProcessor moduleMetadataProcessor, List<DependencySubstitutionRules> rules) {
            return new DefaultGlobalDependencyResolutionRules(componentMetadataProcessorFactory, moduleMetadataProcessor, rules);
        }

        ConfigurationResolver createDependencyResolver(ArtifactDependencyResolver artifactDependencyResolver,
                                                       RepositoriesSupplier repositoriesSupplier,
                                                       GlobalDependencyResolutionRules metadataHandler,
                                                       ComponentIdentifierFactory componentIdentifierFactory,
                                                       ResolutionResultsStoreFactory resolutionResultsStoreFactory,
                                                       StartParameter startParameter,
                                                       AttributesSchemaInternal attributesSchema,
                                                       VariantTransformRegistry variantTransforms,
                                                       ImmutableModuleIdentifierFactory moduleIdentifierFactory,
                                                       ImmutableAttributesFactory attributesFactory,
                                                       BuildOperationExecutor buildOperationExecutor,
                                                       ArtifactTypeRegistry artifactTypeRegistry,
                                                       ComponentSelectorConverter componentSelectorConverter,
                                                       AttributeContainerSerializer attributeContainerSerializer,
                                                       BuildState currentBuild,
                                                       TransformedVariantFactory transformedVariantFactory,
                                                       DependencyVerificationOverride dependencyVerificationOverride,
                                                       ComponentSelectionDescriptorFactory componentSelectionDescriptorFactory) {
            return new ErrorHandlingConfigurationResolver(
                new ShortCircuitEmptyConfigurationResolver(
                    new DefaultConfigurationResolver(
                        artifactDependencyResolver,
                        repositoriesSupplier,
                        metadataHandler,
                        resolutionResultsStoreFactory,
                        startParameter.isBuildProjectDependencies(),
                        attributesSchema,
                        new DefaultArtifactTransforms(
                            new ConsumerProvidedVariantFinder(
                                variantTransforms,
                                attributesSchema,
                                attributesFactory),
                            attributesSchema,
                            attributesFactory,
                            transformedVariantFactory
                        ),
                        moduleIdentifierFactory,
                        buildOperationExecutor,
                        artifactTypeRegistry,
                        componentSelectorConverter,
                        attributeContainerSerializer,
                        currentBuild.getBuildIdentifier(),
                        new AttributeDesugaring(attributesFactory),
                        dependencyVerificationOverride,
                        componentSelectionDescriptorFactory),
                    componentIdentifierFactory,
                    moduleIdentifierFactory,
                    currentBuild.getBuildIdentifier()));
        }

        ArtifactPublicationServices createArtifactPublicationServices(ServiceRegistry services) {
            return new DefaultArtifactPublicationServices(services);
        }

        DependencyResolutionServices createDependencyResolutionServices(ServiceRegistry services) {
            return new DefaultDependencyResolutionServices(services, domainObjectContext);
        }

        ArtifactResolutionQueryFactory createArtifactResolutionQueryFactory(ConfigurationContainerInternal configurationContainer,
                                                                            RepositoriesSupplier repositoriesSupplier,
                                                                            ResolveIvyFactory ivyFactory,
                                                                            GlobalDependencyResolutionRules metadataHandler,
                                                                            ComponentTypeRegistry componentTypeRegistry,
                                                                            ImmutableAttributesFactory attributesFactory,
                                                                            ComponentMetadataSupplierRuleExecutor executor) {
            return new DefaultArtifactResolutionQueryFactory(configurationContainer, repositoriesSupplier, ivyFactory, metadataHandler, componentTypeRegistry, attributesFactory, executor);

        }

        RepositoriesSupplier createRepositoriesSupplier(RepositoryHandler repositoryHandler, DependencyResolutionManagementInternal drm, DomainObjectContext context) {
            return () -> {
                List<ResolutionAwareRepository> repositories = collectRepositories(repositoryHandler);
                if (context.isScript() || context.isDetachedState()) {
                    return repositories;
                }
                DependencyResolutionManagementInternal.RepositoriesModeInternal mode = drm.getConfiguredRepositoriesMode();
                if (mode.useProjectRepositories()) {
                    if (repositories.isEmpty()) {
                        repositories = collectRepositories(drm.getRepositories());
                    }
                } else {
                    repositories = collectRepositories(drm.getRepositories());
                }
                return repositories;
            };
        }

        private static List<ResolutionAwareRepository> collectRepositories(RepositoryHandler repositoryHandler) {
            return repositoryHandler.stream()
                .map(ResolutionAwareRepository.class::cast)
                .collect(Collectors.toList());
        }
    }

    private static class DefaultDependencyResolutionServices implements DependencyResolutionServices {

        private final ServiceRegistry services;
        private final DomainObjectContext domainObjectContext;

        private DefaultDependencyResolutionServices(ServiceRegistry services, DomainObjectContext domainObjectContext) {
            this.services = services;
            this.domainObjectContext = domainObjectContext;
        }

        @Override
        public RepositoryHandler getResolveRepositoryHandler() {
            return services.get(RepositoryHandler.class);
        }

        @Override
        public ConfigurationContainerInternal getConfigurationContainer() {
            return services.get(ConfigurationContainerInternal.class);
        }

        @Override
        public DependencyHandler getDependencyHandler() {
            return services.get(DependencyHandler.class);
        }

        @Override
        public DependencyLockingHandler getDependencyLockingHandler() {
            return services.get(DependencyLockingHandler.class);
        }

        @Override
        public ImmutableAttributesFactory getAttributesFactory() {
            return services.get(ImmutableAttributesFactory.class);
        }

        @Override
        public AttributesSchema getAttributesSchema() {
            return services.get(AttributesSchema.class);
        }

        @Override
        public ObjectFactory getObjectFactory() {
            return services.get(ObjectFactory.class);
        }
    }

    private static class DefaultArtifactPublicationServices implements ArtifactPublicationServices {

        private final ServiceRegistry services;

        public DefaultArtifactPublicationServices(ServiceRegistry services) {
            this.services = services;
        }

        @Override
        public RepositoryHandler createRepositoryHandler() {
            Instantiator instantiator = services.get(Instantiator.class);
            BaseRepositoryFactory baseRepositoryFactory = services.get(BaseRepositoryFactory.class);
            CollectionCallbackActionDecorator callbackDecorator = services.get(CollectionCallbackActionDecorator.class);
            return instantiator.newInstance(DefaultRepositoryHandler.class, baseRepositoryFactory, instantiator, callbackDecorator);
        }

        @Override
        public ArtifactPublisher createArtifactPublisher() {
            DefaultArtifactPublisher publisher = new DefaultArtifactPublisher(
                    services.get(LocalConfigurationMetadataBuilder.class),
                    new DefaultIvyModuleDescriptorWriter(services.get(ComponentSelectorConverter.class))
            );
            return new IvyContextualArtifactPublisher(services.get(IvyContextManager.class), publisher);
        }

    }
}
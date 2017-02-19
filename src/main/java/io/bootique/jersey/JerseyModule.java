package io.bootique.jersey;

import com.google.inject.Binder;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;
import io.bootique.ConfigModule;
import io.bootique.config.ConfigurationFactory;
import io.bootique.jetty.JettyModule;
import io.bootique.jetty.MappedServlet;
import org.glassfish.jersey.server.ResourceConfig;

import javax.ws.rs.container.DynamicFeature;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Feature;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class JerseyModule extends ConfigModule {

	private String urlPattern = "/*";
	private Class<? extends Application> application;
	private Collection<Class<?>> resources = new HashSet<>();
	private Collection<String> packageRoots = new HashSet<>();

	/**
	 * @param binder
	 *            DI binder passed to the Module that invokes this method.
	 * @since 0.11
	 * @return returns a {@link Multibinder} for JAX-RS Features.
	 */
	public static Multibinder<Feature> contributeFeatures(Binder binder) {
		return Multibinder.newSetBinder(binder, Feature.class);
	}

	/**
	 * @param binder
	 *            DI binder passed to the Module that invokes this method.
	 * @since 0.12
	 * @return returns a {@link Multibinder} for JAX-RS DynamicFeatures.
	 */
	public static Multibinder<DynamicFeature> contributeDynamicFeatures(Binder binder) {
		return Multibinder.newSetBinder(binder, DynamicFeature.class);
	}

	/**
	 * @param binder
	 *            DI binder passed to the Module that invokes this method.
	 * @since 0.15
	 * @return returns a {@link Multibinder} for explicitly registered JAX-RS
	 *         resource types.
	 */
	public static Multibinder<Object> contributeResources(Binder binder) {
		return Multibinder.newSetBinder(binder, Key.get(Object.class, JerseyResource.class));
	}

	/**
	 * @param binder
	 *            DI binder passed to the Module that invokes this method.
	 * @since 0.15
	 * @return returns a {@link Multibinder} for packages that contain JAX-RS
	 *         resource classes.
	 */
	public static Multibinder<Package> contributePackages(Binder binder) {
		return Multibinder.newSetBinder(binder, Package.class);
	}

	@Override
	public void configure(Binder binder) {

		JettyModule.contributeMappedServlets(binder)
				.addBinding().to(Key.get(MappedServlet.class, JerseyServlet.class));

		// trigger extension points creation and provide default contributions
		JerseyModule.contributeFeatures(binder);
		JerseyModule.contributeDynamicFeatures(binder);
		JerseyModule.contributePackages(binder);
		JerseyModule.contributeResources(binder);
	}

	@Singleton
	@Provides
	private ResourceConfig createResourceConfig(Injector injector, Set<Feature> features,
			Set<DynamicFeature> dynamicFeatures, @JerseyResource Set<Object> resources, Set<Package> packages) {

		ResourceConfig config = application != null ? ResourceConfig.forApplicationClass(application)
				: new ResourceConfig();

		// loaded deprecated configs provided via the builder
		this.packageRoots.forEach(p -> config.packages(true, p));
		this.resources.forEach(r -> config.register(r));
		
		packages.forEach(p -> config.packages(true, p.getName()));
		resources.forEach(r -> config.register(r));

		features.forEach(f -> config.register(f));
		dynamicFeatures.forEach(df -> config.register(df));

		// TODO: make this pluggable?
		config.register(ResourceModelDebugger.class);

		// register Guice Injector as a service in Jersey HK2, and
		// GuiceBridgeFeature as a
		GuiceBridgeFeature.register(config, injector);

		return config;
	}

	@JerseyServlet
	@Provides
	@Singleton
	private MappedServlet createJerseyServlet(ConfigurationFactory configFactory, ResourceConfig config) {
		return configFactory.config(JerseyServletFactory.class, configPrefix).initUrlPatternIfNotSet(urlPattern)
				.createJerseyServlet(config);
	}
}

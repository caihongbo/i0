package com.thoughtworks.i0;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.persist.PersistFilter;
import com.google.inject.persist.jpa.JpaPersistModule;
import com.google.inject.servlet.ServletModule;
import com.sun.jersey.guice.JerseyServletModule;
import com.sun.jersey.guice.spi.container.servlet.GuiceContainer;
import com.thoughtworks.i0.config.Configuration;
import com.thoughtworks.i0.config.builder.ConfigurationBuilder;
import com.thoughtworks.i0.internal.util.ClassScanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.Filter;
import javax.servlet.annotation.WebFilter;
import javax.servlet.annotation.WebInitParam;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.Set;

import static com.google.common.base.Joiner.on;
import static com.google.common.base.Optional.of;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Iterables.toArray;
import static com.google.common.collect.Iterables.transform;
import static com.sun.jersey.api.core.PackagesResourceConfig.PROPERTY_PACKAGES;
import static com.thoughtworks.i0.config.builder.ConfigurationBuilder.config;
import static com.thoughtworks.i0.internal.util.ServletAnnotations.LOG_FORMATTER;
import static com.thoughtworks.i0.internal.util.ServletAnnotations.urlPatterns;
import static com.thoughtworks.i0.internal.util.TypePredicates.*;

public class ApplicationModule extends AbstractModule {
    private final Application application;

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    private Optional<Configuration> configuration = Optional.absent();

    public ApplicationModule() {
        checkState(getClass().isAnnotationPresent(Application.class), "missing @Application annotation for application module '" + getClass().getName() + "'");
        this.application = getClass().getAnnotation(Application.class);
    }

    @Override
    protected final void configure() {
        if (application.autoScanning()) {
            String currentPackage = getClass().getPackage().getName();
            scan(currentPackage);
            scanResources(application.api(), currentPackage);
        }

        if (getClass().isAnnotationPresent(PersistUnit.class)) {
            Preconditions.checkArgument(getConfiguration().getDatabase().isPresent(), "No database configuration found");
            install(new JpaPersistModule(getClass().getAnnotation(PersistUnit.class).value()).properties(getConfiguration().getDatabase().get().toProperties()));
            install(new ServletModule() {
                @Override
                protected void configureServlets() {
                    filter("/*").through(PersistFilter.class);
                }
            });
        }
    }

    void setConfiguration(Configuration configuration) {
        this.configuration = of(configuration);
    }

    protected final Configuration getConfiguration() {
        if (!configuration.isPresent()) configuration = of(createDefaultConfiguration(config()));
        return configuration.get();
    }

    protected Configuration createDefaultConfiguration(ConfigurationBuilder config) {
        return config.http().port(8080).end().build();
    }

    protected void scan(String... packages) {
        install(new AutoScanningServletModule(packages));
    }

    protected void scanResources(final String path, final String... packages) {
        install(new JerseyServletModule() {
            @Override
            protected void configureServlets() {
                ImmutableSet<String> packageSet = ImmutableSet.<String>builder().add(packages).add("com.fasterxml.jackson.jaxrs.json").build();
                serve(path).with(GuiceContainer.class, new ImmutableMap.Builder<String, String>()
                        .put(PROPERTY_PACKAGES, on(";").skipNulls().join(packageSet)).build());
            }
        });
    }

    public String name() {
        return application.name().startsWith("/") ? application.name() : "/" + application.name();
    }

    private class AutoScanningServletModule extends ServletModule {
        private final ClassScanner scanner;
        private final String[] packages;

        public AutoScanningServletModule(String... packages) {
            this.packages = packages;
            this.scanner = new ClassScanner(packages);
        }

        @Override
        protected void configureServlets() {
            if (logger.isInfoEnabled())
                logger.info("Scanning for servlet, filter and module classes in packages:\n  {}", on("\n  ").join(packages));

            scanHttpServletClasses();
            scanFilterClasses();
            scanModuleClasses();
        }

        private void scanModuleClasses() {
            Set<Class<?>> moduleClasses = scanner.findBy(isModule);
            for (Class<?> moduleClass : moduleClasses)
                try {
                    install((Module) moduleClass.getConstructor().newInstance());
                } catch (InstantiationException | NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
                    logger.warn("Can not instantiate moduleClass '" + moduleClass.getName() + "'", e);
                }

            logFound("Module", moduleClasses, null);
        }

        private void scanFilterClasses() {
            Set<Class<?>> filterClasses = scanner.findBy(isFilter);
            for (Class<?> filter : filterClasses)
                bind(filter.getAnnotation(WebFilter.class), (Class<? extends Filter>) filter);
            logFound("Filter", filterClasses, LOG_FORMATTER);
        }

        private void scanHttpServletClasses() {
            Set<Class<?>> servletClasses = scanner.findBy(isHttpServlet);
            for (Class<?> servlet : servletClasses)
                bind(servlet.getAnnotation(WebServlet.class), (Class<? extends HttpServlet>) servlet);
            logFound("Servlet", servletClasses, LOG_FORMATTER);
        }

        private void logFound(String type, Set<Class<?>> found, Function<Class<?>, String> formatter) {
            if (logger.isInfoEnabled())
                logger.info(found.isEmpty() ? ("No " + type.toLowerCase() + " classes found") : (type + " classes found:\n  {}"),
                        on("\n  ").join(formatter != null ? transform(found, formatter) : found));
        }


        private void bind(WebServlet servlet, Class<? extends HttpServlet> servletClass) {
            serve(urlPatterns(servletClass).asList()).with(servletClass, initParams(servlet.initParams()));
        }

        private void bind(WebFilter filter, Class<? extends Filter> filterClass) {
            filter(urlPatterns(filterClass).asList()).through(filterClass, initParams(filter.initParams()));
        }

        private ServletKeyBindingBuilder serve(ImmutableList<String> urlPatterns) {
            return serve(urlPatterns.get(0), tail(urlPatterns));
        }

        private FilterKeyBindingBuilder filter(ImmutableList<String> urlPatterns) {
            return filter(urlPatterns.get(0), tail(urlPatterns));
        }

        private Map<String, String> initParams(WebInitParam[] initParams) {
            ImmutableMap.Builder<String, String> builder = new ImmutableMap.Builder<>();
            for (WebInitParam initParam : initParams)
                builder.put(initParam.name(), initParam.value());
            return builder.build();
        }

        private String[] tail(ImmutableList<String> urlPatterns) {
            return toArray(urlPatterns.subList(1, urlPatterns.size()), String.class);
        }
    }

}

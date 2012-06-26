package de.deepamehta.core.service;

import de.deepamehta.core.Association;
import de.deepamehta.core.DeepaMehtaTransaction;
import de.deepamehta.core.Topic;
import de.deepamehta.core.TopicType;
import de.deepamehta.core.model.CompositeValue;
import de.deepamehta.core.model.SimpleValue;
import de.deepamehta.core.model.TopicModel;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.osgi.service.http.HttpContext;
import org.osgi.service.http.HttpService;
import org.osgi.util.tracker.ServiceTracker;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.util.ArrayList;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Logger;
import java.io.InputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;



/**
 * Base class for plugin developers to derive their plugins from.
 */
public class Plugin implements BundleActivator, EventHandler {

    // ------------------------------------------------------------------------------------------------------- Constants

    private static final String PLUGIN_CONFIG_FILE = "/plugin.properties";
    private static final String PLUGIN_READY = "dm4/core/plugin_ready";

    // ------------------------------------------------------------------------------------------------- Class Variables

    private static final Set<String> readyPlugins = new HashSet();

    // ---------------------------------------------------------------------------------------------- Instance Variables

    private BundleContext context;

    private String pluginId;                // This bundle's symbolic name, e.g. "de.deepamehta.webclient".
    private String pluginName;              // This bundle's name = POM project name.
    private String pluginClass;
    private String pluginPackage;
    private Bundle pluginBundle;
    private Topic  pluginTopic;             // Represents this plugin in DB. Holds plugin migration number.

    private Properties configProperties;    // Read from file "plugin.properties"

    // For tracking the state of dependent plugins.
    // Key: plugin ID, value: availability (true=available)
    private Map<String, Boolean> dependencyState;

    // Consumed services
    protected DeepaMehtaService dms;
    private WebPublishingService webPublishingService;
    private HttpService httpService;
    private EventAdmin eventService;

    // Provided OSGi service
    private ServiceRegistration registration;

    // Provided REST service
    private RestResource restResource;

    private List<ServiceTracker> serviceTrackers = new ArrayList();

    private Logger logger = Logger.getLogger(getClass().getName());

    // -------------------------------------------------------------------------------------------------- Public Methods

    public String getId() {
        return pluginId;
    }

    public String getName() {
        return pluginName;
    }

    /**
     * Returns a plugin configuration property (as read from file "plugin.properties")
     * or <code>null</code> if no such property exists.
     */
    public String getConfigProperty(String key) {
        return getConfigProperty(key, null);
    }

    /**
     * Uses the plugin bundle's class loader to load a class by name.
     *
     * @return  the class, or <code>null</code> if the class is not found.
     */
    public Class loadClass(String className) {
        try {
            return pluginBundle.loadClass(className);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    /**
     * Returns the migration class name for the given migration number.
     *
     * @return  the fully qualified migration class name, or <code>null</code> if the migration package is unknown.
     *          This is the case if the plugin bundle contains no Plugin subclass and the "pluginPackage" config
     *          property is not set.
     */
    public String getMigrationClassName(int migrationNr) {
        if (pluginPackage.equals("de.deepamehta.core.service")) {
            return null;    // migration package is unknown
        }
        //
        return pluginPackage + ".migrations.Migration" + migrationNr;
    }

    // FIXME: should be private
    public void setMigrationNr(int migrationNr) {
        pluginTopic.setChildTopicValue("dm4.core.plugin_migration_nr", new SimpleValue(migrationNr));
    }

    /**
     * Uses the plugin bundle's class loader to find a resource.
     *
     * @return  A InputStream object or null if no resource with this name is found.
     */
    public InputStream getResourceAsStream(String name) throws IOException {
        // We always use the plugin bundle's class loader to access the resource.
        // getClass().getResource() would fail for generic plugins (plugin bundles not containing a plugin
        // subclass) because the core bundle's class loader would be used and it has no access.
        URL url = pluginBundle.getResource(name);
        if (url != null) {
            return url.openStream();
        } else {
            return null;
        }
    }

    @Override
    public String toString() {
        return "plugin \"" + pluginName + "\"";
    }



    // **************************************
    // *** BundleActivator Implementation ***
    // **************************************



    @Override
    public void start(BundleContext context) {
        try {
            this.context = context;
            this.pluginBundle = context.getBundle();
            this.pluginId = pluginBundle.getSymbolicName();
            this.pluginName = (String) pluginBundle.getHeaders().get("Bundle-Name");
            this.pluginClass = (String) pluginBundle.getHeaders().get("Bundle-Activator");
            //
            logger.info("========== Starting " + this + " ==========");
            //
            this.configProperties = readConfigFile();
            this.pluginPackage = getConfigProperty("pluginPackage", getClass().getPackage().getName());
            this.dependencyState = initDependencies();
            //
            if (dependencyState.size() > 0) {
                registerEventListener();
            }
            //
            createServiceTracker(DeepaMehtaService.class.getName());
            createServiceTracker(WebPublishingService.class.getName());
            createServiceTracker(HttpService.class.getName());
            createServiceTracker(EventAdmin.class.getName());
            createServiceTrackers();
        } catch (Exception e) {
            logger.severe("Starting " + this + " failed:");
            e.printStackTrace();
            // Note: an exception thrown from here is swallowed by the container without reporting
            // and let File Install retry to start the bundle endlessly.
        }
    }

    @Override
    public void stop(BundleContext context) {
        logger.info("========== Stopping " + this + " ==========");
        //
        for (ServiceTracker serviceTracker : serviceTrackers) {
            serviceTracker.close();
        }
        //
        unregisterPluginService();
    }



    // *************
    // *** Hooks ***
    // *************



    public void postInstallPluginHook() {
    }

    public void allPluginsReadyHook() {
    }

    // ---

    public void serviceArrived(PluginService service) {
    }

    public void serviceGone(PluginService service) {
    }

    // ---

    public void preCreateHook(TopicModel model, ClientState clientState) {
    }

    public void postCreateHook(Topic topic, ClientState clientState, Directives directives) {
    }

    // ---

    public void preUpdateHook(Topic topic, TopicModel newModel, Directives directives) {
    }

    public void postUpdateHook(Topic topic, TopicModel newModel, TopicModel oldModel, ClientState clientState,
                                                                                      Directives directives) {
    }

    // ---

    public void postRetypeAssociationHook(Association assoc, String oldTypeUri, Directives directives) {
    }

    // ---

    public void preDeleteAssociationHook(Association assoc, Directives directives) {
    }

    public void postDeleteAssociationHook(Association assoc, Directives directives) {
    }

    // ---

    public void preSendTopicHook(Topic topic, ClientState clientState) {
    }

    public void preSendTopicTypeHook(TopicType topicType, ClientState clientState) {
    }

    // ---

    public void providePropertiesHook(Topic topic) {
    }

    public void providePropertiesHook(Association assoc) {
    }

    // ---

    /**
     * Allows a plugin to modify type definitions -- exisisting ones <i>and</i> future ones.
     * Plugins get a opportunity to visit (and modify) each type definition extacly once.
     * <p>
     * This hook is triggered in 2 situations:
     * <ul>
     *  <li>for each type that <i>exists</i> already while a plugin clean install.
     *  <li>for types created (interactively by the user, or programmatically by a migration) <i>after</i>
     *      the plugin has been installed.
     * </ul>
     * This hook is typically used by plugins which provide cross-cutting concerns by affecting <i>all</i>
     * type definitions of a DeepaMehta installation. Typically such a plugin adds new data fields to types
     * or relates types with specific topics.
     * <p>
     * Examples of plugins which use this hook:
     * <ul>
     *  <li>The "DeepaMehta 4 Workspaces" plugin adds a "Workspaces" field to all types.
     *  <li>The "DeepaMehta 4 Time" plugin adds timestamp fields to all types.
     *  <li>The "DeepaMehta 4 Access Control" plugin adds a "Creator" field to all types and relates them to a user.
     * </ul>
     *
     * @param   topicType   the type to be modified. The passed object is actually an instance of a {@link TopicType}
     *                      subclass that is backed by the database. That is, modifications by e.g.
     *                      {@link TopicType#addDataField} are persistent.
     *                      <p>
     *                      Note: at the time the hook is triggered the type exists already in the database, in
     *                      particular the underlying type topic has an ID already. That is, the type is ready for
     *                      e.g. being related to other topics.
     */
    public void modifyTopicTypeHook(TopicType topicType, ClientState clientState) {
    }

    // ---

    public CommandResult executeCommandHook(String command, CommandParams params, ClientState clientState) {
        return null;
    }



    // ------------------------------------------------------------------------------------------------- Private Methods

    private void createServiceTrackers() {
        String consumedServiceInterfaces = getConfigProperty("consumedServiceInterfaces");
        if (consumedServiceInterfaces != null) {
            String[] serviceInterfaces = consumedServiceInterfaces.split(", *");
            for (int i = 0; i < serviceInterfaces.length; i++) {
                createServiceTracker(serviceInterfaces[i]);
            }
        }
    }

    private void createServiceTracker(final String serviceInterface) {
        ServiceTracker serviceTracker = new ServiceTracker(context, serviceInterface, null) {

            @Override
            public Object addingService(ServiceReference serviceRef) {
                Object service = super.addingService(serviceRef);
                if (service instanceof DeepaMehtaService) {
                    logger.info("Adding DeepaMehta 4 core service to plugin \"" + pluginName + "\"");
                    dms = (DeepaMehtaService) service;
                    checkServiceAvailability();
                } else if (service instanceof WebPublishingService) {
                    logger.info("Adding Web Publishing service to plugin \"" + pluginName + "\"");
                    webPublishingService = (WebPublishingService) service;
                    checkServiceAvailability();
                } else if (service instanceof HttpService) {
                    logger.info("Adding HTTP service to plugin \"" + pluginName + "\"");
                    httpService = (HttpService) service;
                    checkServiceAvailability();
                } else if (service instanceof EventAdmin) {
                    logger.info("Adding Event Admin service to plugin \"" + pluginName + "\"");
                    eventService = (EventAdmin) service;
                    checkServiceAvailability();
                } else if (service instanceof PluginService) {
                    logger.info("Adding plugin service \"" + serviceInterface + "\" to plugin \"" + pluginName + "\"");
                    // trigger hook
                    serviceArrived((PluginService) service);
                }
                //
                return service;
            }

            @Override
            public void removedService(ServiceReference ref, Object service) {
                if (service == dms) {
                    logger.info("Removing DeepaMehta 4 core service from plugin \"" + pluginName + "\"");
                    unregisterPlugin();
                    dms = null;
                } else if (service == webPublishingService) {
                    logger.info("Removing Web Publishing service from plugin \"" + pluginName + "\"");
                    unregisterRestResources();
                    webPublishingService = null;
                } else if (service == httpService) {
                    logger.info("Removing HTTP service from plugin \"" + pluginName + "\"");
                    unregisterWebResources();
                    httpService = null;
                } else if (service == eventService) {
                    logger.info("Removing Event Admin service from plugin \"" + pluginName + "\"");
                    eventService = null;
                } else if (service instanceof PluginService) {
                    logger.info("Removing plugin service \"" + serviceInterface + "\" from plugin \"" +
                        pluginName + "\"");
                    // trigger hook
                    serviceGone((PluginService) service);
                }
                super.removedService(ref, service);
            }
        };
        serviceTrackers.add(serviceTracker);
        serviceTracker.open();
    }

    // ---

    /**
     * Checks if both required OSGi services (DeepaMehtaService and HttpService) are available,
     * and if so, initializes the plugin. ### FIXDOC
     */
    private void checkServiceAvailability() {
        if (dms != null && webPublishingService != null && httpService != null && eventService != null
                                                                               && dependenciesAvailable()) {
            initPlugin();
            pluginReady();
            postPluginReadyEvent();
            dms.checkAllPluginsReady();
        }
    }

    // ---

    private void pluginReady() {
        readyPlugins.add(pluginId);
    }

    private boolean isPluginReady(String pluginId) {
        return readyPlugins.contains(pluginId);
    }

    // ---

    /**
     * Initializes the plugin. This comprises:
     * - install the plugin in the database
     * - register the plugin at the DeepaMehta core service
     * - register the plugin's OSGi service at the OSGi framework
     * - register the plugin's static web resources at the OSGi HTTP service
     * - register the plugin's REST resources at the OSGi HTTP service
     *
     * These are the tasks which rely on both, the DeepaMehtaService and the HttpService.
     * This method is called once both services become available.
     */
    private void initPlugin() {
        try {
            logger.info("----- Initializing " + this + " -----");
            installPlugin();                    // relies on DeepaMehtaService
            registerPlugin();                   // relies on DeepaMehtaService (and committed migrations)
            registerPluginService();
            registerWebResources();             // relies on HttpService
            registerRestResources();            // relies on HttpService and DeepaMehtaService (and registered plugin)
            logger.info("----- Initialization of " + this + " complete -----");
            // ### FIXME: should we call registerPlugin() at last?
            // Currently if e.g. registerPluginService() fails the plugin is still registered at the DeepaMehta core.
        } catch (Exception e) {
            throw new RuntimeException("Initialization of " + this + " failed", e);
        }
    }

    /**
     * Installs the plugin in the database. This comprises:
     * - create topic of type "Plugin"
     * - run migrations
     * - trigger POST_INSTALL_PLUGIN hook
     * - trigger MODIFY_TOPIC_TYPE hook (multiple times)
     */
    private void installPlugin() {
        DeepaMehtaTransaction tx = dms.beginTx();
        try {
            boolean isCleanInstall = initPluginTopic();
            runPluginMigrations(isCleanInstall);
            if (isCleanInstall) {
                postInstallPluginHook();  // trigger hook
                introduceTypesToPlugin();
            }
            tx.success();
        } catch (Exception e) {
            logger.warning("ROLLBACK! (" + this + ")");
            throw new RuntimeException("Installation of " + this + " failed", e);
        } finally {
            tx.finish();
        }
    }

    // ---

    /**
     * Registers the plugin's OSGi service at the OSGi framework.
     * If the plugin doesn't provide a service nothing is performed.
     */
    private void registerPluginService() {
        String serviceInterface = getConfigProperty("providedServiceInterface");
        if (serviceInterface == null) {
            return;
        }
        //
        try {
            logger.info("Registering service \"" + serviceInterface + "\" of " + this + " at OSGi framework");
            registration = context.registerService(serviceInterface, this, null);
        } catch (Exception e) {
            throw new RuntimeException("Registering service of " + this + " at OSGi framework failed " +
                "(serviceInterface=\"" + serviceInterface + "\")", e);
        }
    }

    private void unregisterPluginService() {
        String serviceInterface = getConfigProperty("providedServiceInterface");
        if (serviceInterface == null) {
            return;
        }
        //
        try {
            logger.info("Unregistering service \"" + serviceInterface + "\" of " + this + " at OSGi framework");
            registration.unregister();
        } catch (Exception e) {
            throw new RuntimeException("Unregistering service of " + this + " at OSGi framework failed " +
                "(serviceInterface=\"" + serviceInterface + "\")", e);
        }
    }

    // ---

    /**
     * Registers the plugin at the DeepaMehta core service. From that moment the plugin takes part of the
     * core service control flow, that is the plugin's hooks are triggered.
     */
    private void registerPlugin() {
        logger.info("Registering " + this + " at DeepaMehta 4 core service");
        dms.registerPlugin(this);
    }

    private void unregisterPlugin() {
        logger.info("Unregistering " + this + " at DeepaMehta 4 core service");
        dms.unregisterPlugin(pluginId);
    }

    // === Web Resources ===

    private void registerWebResources() {
        String namespace = null;
        try {
            namespace = getWebResourcesNamespace();
            if (namespace != null) {
                logger.info("Registering Web resources of " + this + " at namespace \"" + namespace + "\"");
                httpService.registerResources(namespace, "/web", new PluginHTTPContext());
            } else {
                logger.info("Registering Web resources of " + this + " ABORTED -- no Web resources provided");
            }
        } catch (Exception e) {
            throw new RuntimeException("Registering Web resources of " + this + " failed " +
                "(namespace=\"" + namespace + "\")", e);
        }
    }

    private void unregisterWebResources() {
        String namespace = getWebResourcesNamespace();
        if (namespace != null) {
            logger.info("Unregistering Web resources of " + this);
            httpService.unregister(namespace);
        }
    }

    // ---

    private String getWebResourcesNamespace() {
        return pluginBundle.getEntry("/web") != null ? "/" + pluginId : null;
    }

    // ---

    /**
     * Custom HttpContext to map resource name "/" to URL "/index.html"
     */
    private class PluginHTTPContext implements HttpContext {

        private HttpContext httpContext;

        private PluginHTTPContext() {
            httpContext = httpService.createDefaultHttpContext();
        }

        // ---

        @Override
        public URL getResource(String name) {
            try {
                URL url;
                if (name.equals("web/")) {
                    url = new URL("bundle://" + pluginBundle.getBundleId() + ".0:1/web/index.html");
                } else {
                    url = httpContext.getResource(name);
                }
                // logger.info("### Mapping resource name \"" + name + "\" for plugin \"" +
                //     pluginName + "\"\n          => URL \"" + url + "\"");
                return url;
            } catch (MalformedURLException e) {
                throw new RuntimeException("Mapping resource name \"" + name + "\" for plugin \"" +
                    pluginName + "\" to an URL failed");
            }
        }

        @Override
        public String getMimeType(String name) {
            return httpContext.getMimeType(name);
        }

        @Override
        public boolean handleSecurity(HttpServletRequest request, HttpServletResponse response)
                                                                            throws java.io.IOException {
            return httpContext.handleSecurity(request, response);
        }
    }

    // === REST Resources ===

    private void registerRestResources() {
        String uriPath = null;
        try {
            uriPath = webPublishingService.getUriPath(this);
            if (uriPath != null) {
                logger.info("Registering REST resources of " + this + " at namespace \"" + uriPath + "\"");
                restResource = webPublishingService.addResource(this, getProviderClasses());
            } else {
                logger.info("Registering REST resources of " + this + " ABORTED -- no REST resources provided");
            }
        } catch (Exception e) {
            unregisterWebResources();
            throw new RuntimeException("Registering REST resources of " + this + " failed " +
                "(namespace=\"" + uriPath + "\")", e);
        }
    }

    private void unregisterRestResources() {
        if (restResource != null) {
            logger.info("Unregistering REST resources of " + this);
            webPublishingService.removeResource(restResource);
        }
    }

    // ---

    public Set<Class<?>> getProviderClasses() throws IOException {
        Set<Class<?>> providerClasses = new HashSet();
        String providerPackage = ("/" + pluginPackage + ".provider").replace('.', '/');
        Enumeration<String> e = pluginBundle.getEntryPaths(providerPackage);
        logger.fine("### Scanning package " + pluginPackage + ".provider");
        if (e != null) {
            while (e.hasMoreElements()) {
                String entryPath = e.nextElement();
                entryPath = entryPath.substring(0, entryPath.length() - 6);     // cut ".class"
                String className = entryPath.replace('/', '.');
                logger.fine("  # Found provider class: " + className);
                Class providerClass = loadClass(className);
                if (providerClass == null) {
                    throw new RuntimeException("Loading provider class \"" + className + "\" failed");
                }
                providerClasses.add(providerClass);
            }
        }
        return providerClasses;
    }

    // === Config Properties ===

    private Properties readConfigFile() {
        try {
            Properties properties = new Properties();
            InputStream in = getResourceAsStream(PLUGIN_CONFIG_FILE);
            if (in != null) {
                logger.info("Reading config file \"" + PLUGIN_CONFIG_FILE + "\" for " + this);
                properties.load(in);
            } else {
                logger.info("Reading config file \"" + PLUGIN_CONFIG_FILE + "\" for " + this +
                    " ABORTED -- file does not exist");
            }
            return properties;
        } catch (Exception e) {
            throw new RuntimeException("Reading config file \"" + PLUGIN_CONFIG_FILE + "\" for " + this + " failed", e);
        }
    }

    private String getConfigProperty(String key, String defaultValue) {
        return configProperties.getProperty(key, defaultValue);
    }

    // === Model Dependencies ===

    private Map<String, Boolean> initDependencies() {
        Map<String, Boolean> dependencyState = new HashMap();
        String importModels = getConfigProperty("importModels");
        if (importModels != null) {
            String[] pluginIDs = importModels.split(", *");
            for (int i = 0; i < pluginIDs.length; i++) {
                if (!isPluginReady(pluginIDs[i])) {
                    dependencyState.put(pluginIDs[i], false);
                }
            }
        }
        return dependencyState;
    }

    private boolean hasDependency(String pluginId) {
        return dependencyState.get(pluginId) != null;
    }

    private boolean dependenciesAvailable() {
        for (boolean available : dependencyState.values()) {
            if (!available) {
                return false;
            }
        }
        return true;
    }

    private void registerEventListener() {
        String[] topics = new String[] {PLUGIN_READY};
        Hashtable properties = new Hashtable();
        properties.put(EventConstants.EVENT_TOPIC, topics);
        context.registerService(EventHandler.class.getName(), this, properties);
    }

    @Override
    public void handleEvent(Event event) {
        try {
            if (event.getTopic().equals(PLUGIN_READY)) {
                String pluginId = (String) event.getProperty(EventConstants.BUNDLE_SYMBOLICNAME);
                if (hasDependency(pluginId)) {
                    logger.info("### Receiving PLUGIN_READY event from \"" + pluginId + "\" for " + this);
                    dependencyState.put(pluginId, true);
                    checkServiceAvailability();
                }
            } else {
                throw new RuntimeException("Unexpected event: " + event);
            }
        } catch (Exception e) {
            logger.severe("Handling OSGi event for " + this + " failed (event=" + event + ")");
            e.printStackTrace();
            // Note: we don't throw through the OSGi container here. It would not print out the stacktrace.
            // ### FIXME: should we re-throw to signalize that a plugin has a problem?
        }
    }

    private void postPluginReadyEvent() {
        Properties properties = new Properties();
        properties.put(EventConstants.BUNDLE_SYMBOLICNAME, pluginId);
        eventService.postEvent(new Event(PLUGIN_READY, properties));
    }

    // ---

    /**
     * Creates a Plugin topic in the DB, if not already exists.
     * <p>
     * A Plugin topic represents an installed plugin and is used to track its version.
     *
     * @return  <code>true</code> if a Plugin topic has been created (means: this is a plugin clean install),
     *          <code>false</code> otherwise.
     */
    private boolean initPluginTopic() {
        pluginTopic = findPluginTopic();
        if (pluginTopic != null) {
            logger.info("Installing " + this + " ABORTED -- already installed");
            return false;
        } else {
            logger.info("Installing " + this);
            pluginTopic = dms.createTopic(new TopicModel(pluginId, "dm4.core.plugin",
                new CompositeValue().put("dm4.core.plugin_name", pluginName)
                                    .put("dm4.core.plugin_symbolic_name", pluginId)
                                    .put("dm4.core.plugin_migration_nr", 0)), null);    // FIXME: clientState=null
            return true;
        }
    }

    private Topic findPluginTopic() {
        return dms.getTopic("uri", new SimpleValue(pluginId), false, null);     // fetchComposite=false
    }

    /**
     * Determines the migrations to be run for this plugin and run them.
     */
    private void runPluginMigrations(boolean isCleanInstall) {
        int migrationNr = pluginTopic.getChildTopicValue("dm4.core.plugin_migration_nr").intValue();
        int requiredMigrationNr = Integer.parseInt(getConfigProperty("requiredPluginMigrationNr", "0"));
        int migrationsToRun = requiredMigrationNr - migrationNr;
        logger.info("Running " + migrationsToRun + " plugin migrations (migrationNr=" + migrationNr +
            ", requiredMigrationNr=" + requiredMigrationNr + ")");
        for (int i = migrationNr + 1; i <= requiredMigrationNr; i++) {
            dms.runPluginMigration(this, i, isCleanInstall);
        }
    }

    private void introduceTypesToPlugin() {
        for (String topicTypeUri : dms.getTopicTypeUris()) {
            try {
                // trigger hook
                modifyTopicTypeHook(dms.getTopicType(topicTypeUri, null), null);   // clientState=null (2x)
            } catch (Exception e) {
                throw new RuntimeException("Introducing topic type \"" + topicTypeUri + "\" to " + this + " failed", e);
            }
        }
    }
}

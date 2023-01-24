package com.octopus.teamcity.opentelemetry.server;

import jetbrains.buildServer.controllers.BaseController;
import jetbrains.buildServer.serverSide.ProjectManager;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.SBuildServer;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.web.openapi.*;
import jetbrains.buildServer.web.util.WebUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Date;
import java.util.Map;
import java.util.logging.Logger;

import static com.octopus.teamcity.opentelemetry.common.PluginConstants.*;

public class BuildOverviewExtensionController extends BaseController
{
    private static final Logger LOG = Logger.getLogger(BuildOverviewExtensionController.class.getName());

    private final SBuildServer sBuildServer;
    private final ProjectManager projectManager;
    private final PluginDescriptor pluginDescriptor;
    private final BuildStorageManager buildStorageManager;

    public BuildOverviewExtensionController(
            @NotNull PagePlaces pagePlaces,
            @NotNull SBuildServer sBuildServer,
            @NotNull ProjectManager projectManager,
            @NotNull PluginDescriptor pluginDescriptor,
            @NotNull WebControllerManager controllerManager,
            @NotNull BuildStorageManager buildStorageManager)
    {
        this.sBuildServer = sBuildServer;
        this.projectManager = projectManager;

        this.pluginDescriptor = pluginDescriptor;
        this.buildStorageManager = buildStorageManager;

        String url = "/otel-trace-url.html";

        final SimplePageExtension sakuraPageExtension = new SimplePageExtension(pagePlaces);
        sakuraPageExtension.setPluginName(PLUGIN_NAME);
        sakuraPageExtension.setPlaceId(new PlaceId("SAKURA_BUILD_OVERVIEW"));
        sakuraPageExtension.setIncludeUrl(url);
        sakuraPageExtension.register();

        // we're not showing on the classic UI, as we want it to appear on the build summary
        // but if we do that, it appears twice on the Sakura UI, and it's in an extremely ugly
        // iframe that makes me cry

        //todo: reach out to JetBrains and ask about it

        // final SimplePageExtension classicPageExtension = new SimplePageExtension(pagePlaces);
        // classicPageExtension.setPluginName(PLUGIN_NAME);
        // classicPageExtension.setPlaceId(PlaceId.BUILD_RESULTS_FRAGMENT);
        // classicPageExtension.setIncludeUrl(url);
        // classicPageExtension.register();

        controllerManager.registerController(url, this);
    }

    @Nullable
    @Override
    protected ModelAndView doHandle(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response) {

        //todo: I think we need to do some auto-refreshes until we have data?

        Long buildId = WebUtil.sakuraUIOpened(request)
            ? PluginUIContext.getFromRequest(request).getBuildId()
            : Long.parseLong(request.getParameter("buildId"));

        if (buildId != null) {
            final SBuild build = sBuildServer.findBuildInstanceById(buildId);
            if (build == null) //if it's queued, we wont get it
                return getEmptyState();

            final SProject project = projectManager.findProjectByExternalId(build.getProjectExternalId());

            var features = project.getAvailableFeaturesOfType(PLUGIN_NAME);
            if (!features.isEmpty()) {
                var feature = features.stream().findFirst().get();
                var params = feature.getParameters();

                if (!params.get(PROPERTY_KEY_ENABLED).equals("true"))
                    return getEmptyState();

                if (params.get(PROPERTY_KEY_SERVICE).equals(OTELService.CUSTOM.getValue()))
                    return getEmptyState();

                var traceId = buildStorageManager.getTraceId(build);
                if (traceId == null)
                    return getEmptyState();

                if (params.get(PROPERTY_KEY_SERVICE).equals(OTELService.HONEYCOMB.getValue()))
                    return getHoneycombModelAndView(build, params, traceId);
                if (params.get(PROPERTY_KEY_SERVICE).equals(OTELService.ZIPKIN.getValue()))
                    return getZipkinModelAndView(build, params, traceId);
            }
        }

        return getEmptyState();
    }

    @NotNull
    private ModelAndView getZipkinModelAndView(SBuild build, Map<String, String> params, String traceId) {
        final ModelAndView mv = new ModelAndView(pluginDescriptor.getPluginResourcesPath("buildOverviewZipkinExtension.jsp"));

        var model = mv.getModel();
        model.put("traceId", traceId);
        model.put("endpoint", params.get(PROPERTY_KEY_ENDPOINT).replaceAll("/$", ""));
        return mv;
    }

    @NotNull
    private ModelAndView getEmptyState() {
        return new ModelAndView(pluginDescriptor.getPluginResourcesPath("buildOverviewEmpty.jsp"));
    }

    @NotNull
    private ModelAndView getHoneycombModelAndView(SBuild build, Map<String, String> params, String traceId) {
        final ModelAndView mv = new ModelAndView(pluginDescriptor.getPluginResourcesPath("buildOverviewHoneycombExtension.jsp"));

        var model = mv.getModel();
        model.put("team", params.get(PROPERTY_KEY_HONEYCOMB_TEAM));
        model.put("dataset", params.get(PROPERTY_KEY_HONEYCOMB_DATASET));
        model.put("traceId", traceId);

        //we pad the time to ensure that we get all the spans, just in case we get a slight diff in the
        //timestamps between the traces and the server start time.
        //this will also help us see the whole chain better
        long twoHoursInSeconds = 7200;
        Date startDate = build.getServerStartDate();
        model.put("buildStart", (startDate.getTime() / 1000L) - twoHoursInSeconds);
        var finishDate = build.getFinishDate();
        model.put("buildEnd", ((finishDate == null ? startDate : finishDate).getTime() / 1000L) + twoHoursInSeconds);
        return mv;
    }
}

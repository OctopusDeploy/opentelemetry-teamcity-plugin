package com.octopus.teamcity.opentelemetry.server;

import com.octopus.teamcity.opentelemetry.server.endpoints.OTELEndpointFactory;
import jetbrains.buildServer.controllers.BaseController;
import jetbrains.buildServer.serverSide.ProjectManager;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.SBuildServer;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.web.openapi.*;
import jetbrains.buildServer.web.util.WebUtil;
import org.apache.logging.log4j.CloseableThreadContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static com.octopus.teamcity.opentelemetry.common.PluginConstants.*;

public class BuildOverviewExtensionController extends BaseController
{
    private final SBuildServer sBuildServer;
    private final ProjectManager projectManager;
    private final PluginDescriptor pluginDescriptor;
    private final BuildStorageManager buildStorageManager;
    @NotNull
    private final OTELEndpointFactory otelEndpointFactory;

    public BuildOverviewExtensionController(
            @NotNull PagePlaces pagePlaces,
            @NotNull SBuildServer sBuildServer,
            @NotNull ProjectManager projectManager,
            @NotNull PluginDescriptor pluginDescriptor,
            @NotNull WebControllerManager controllerManager,
            @NotNull BuildStorageManager buildStorageManager,
            @NotNull OTELEndpointFactory otelEndpointFactory)
    {
        this.sBuildServer = sBuildServer;
        this.projectManager = projectManager;

        this.pluginDescriptor = pluginDescriptor;
        this.buildStorageManager = buildStorageManager;
        this.otelEndpointFactory = otelEndpointFactory;

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
            try (var ignored1 = CloseableThreadContext.put("teamcity.build.id", String.valueOf(buildId))) {

                final SBuild build = sBuildServer.findBuildInstanceById(buildId);
                if (build == null) //if it's queued, we won't get it
                    return getEmptyState();

                final SProject project = projectManager.findProjectByExternalId(build.getProjectExternalId());

                var features = project.getAvailableFeaturesOfType(PLUGIN_NAME);
                if (!features.isEmpty()) {
                    var feature = features.stream().findFirst().get();
                    var params = feature.getParameters();

                    if (!params.get(PROPERTY_KEY_ENABLED).equals("true"))
                        return getEmptyState();

                    var traceId = buildStorageManager.getTraceId(build);
                    if (traceId == null)
                        return getEmptyState();

                    var service = otelEndpointFactory.getOTELEndpointHandler(params.get(PROPERTY_KEY_SERVICE));
                    return service.getBuildOverviewModelAndView(build, params, traceId);
                }
            }
        }

        return getEmptyState();
    }

    @NotNull
    private ModelAndView getEmptyState() {
        return new ModelAndView(pluginDescriptor.getPluginResourcesPath("buildOverviewEmpty.jsp"));
    }
}

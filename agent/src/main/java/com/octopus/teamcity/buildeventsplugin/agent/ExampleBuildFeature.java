package com.octopus.teamcity.buildeventsplugin.agent;

import jetbrains.buildServer.agent.AgentBuildFeature;
import jetbrains.buildServer.agent.AgentLifeCycleAdapter;
import jetbrains.buildServer.agent.AgentRunningBuild;
import jetbrains.buildServer.util.EventDispatcher;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class ExampleBuildFeature extends AgentLifeCycleAdapter {

    private static final Logger LOG = LogManager.getLogger();

    public ExampleBuildFeature(EventDispatcher<AgentLifeCycleAdapter> eventDispatcher) {
        eventDispatcher.addListener(this);
    }

    @Override
    public void buildStarted(@NotNull AgentRunningBuild build) {
        Collection<AgentBuildFeature> features = build.getBuildFeaturesOfType("example-build-feature");
        if (!features.isEmpty()) {
            LOG.info("ExampleBuildFeature enabled for build");
        }
    }
}
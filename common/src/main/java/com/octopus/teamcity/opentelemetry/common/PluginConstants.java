package com.octopus.teamcity.opentelemetry.common;

public class PluginConstants {
    private PluginConstants() {}
    public static final String TRACER_INSTRUMENTATION_NAME = "octopus.teamcity.opentelemetry";

    public static final String SERVICE_NAME = "TeamCity";

    public static final String PROPERTY_KEY_ENABLED = "octopus.teamcity.opentelemetry.plugin.enabled";
    public static final String PROPERTY_KEY_SERVICE = "octopus.teamcity.opentelemetry.plugin.service";
    public static final String PROPERTY_KEY_ENDPOINT = "octopus.teamcity.opentelemetry.plugin.endpoint";
    public static final String PROPERTY_KEY_HEADERS = "octopus.teamcity.opentelemetry.plugin.headers";
    public static final String PROPERTY_KEY_HONEYCOMB_TEAM = "octopus.teamcity.opentelemetry.plugin.honeycomb.team";
    public static final String PROPERTY_KEY_HONEYCOMB_DATASET = "octopus.teamcity.opentelemetry.plugin.honeycomb.dataset";

    public static final String ATTRIBUTE_SERVICE_NAME = "service_name";
    public static final String ATTRIBUTE_NAME = "name";
    public static final String ATTRIBUTE_BUILD_TYPE_ID = TRACER_INSTRUMENTATION_NAME + ".build_type_id";
    public static final String ATTRIBUTE_BUILD_TYPE_EXTERNAL_ID = TRACER_INSTRUMENTATION_NAME + ".build_type_external_id";
    public static final String ATTRIBUTE_BUILD_STEP_STATUS = TRACER_INSTRUMENTATION_NAME + ".build_step_status";
    public static final String ATTRIBUTE_PROJECT_NAME = TRACER_INSTRUMENTATION_NAME + ".project_name";
    public static final String ATTRIBUTE_PROJECT_ID = TRACER_INSTRUMENTATION_NAME + ".project_id";
    public static final String ATTRIBUTE_AGENT_NAME = TRACER_INSTRUMENTATION_NAME + ".agent_name";
    public static final String ATTRIBUTE_AGENT_TYPE = TRACER_INSTRUMENTATION_NAME + ".agent_type";
    public static final String ATTRIBUTE_BUILD_NUMBER = TRACER_INSTRUMENTATION_NAME + ".build_number";
    public static final String ATTRIBUTE_COMMIT = TRACER_INSTRUMENTATION_NAME + ".commit";
    public static final String ATTRIBUTE_BRANCH = TRACER_INSTRUMENTATION_NAME + ".branch";
    public static final String ATTRIBUTE_SUCCESS_STATUS = TRACER_INSTRUMENTATION_NAME + ".success_status";
    public static final String ATTRIBUTE_FAILED_TEST_COUNT = TRACER_INSTRUMENTATION_NAME + ".failed_test_count";
    public static final String ATTRIBUTE_BUILD_PROBLEMS_COUNT = TRACER_INSTRUMENTATION_NAME + ".build_problems_count";
    public static final String ATTRIBUTE_TOTAL_ARTIFACT_SIZE = TRACER_INSTRUMENTATION_NAME + ".build_artifacts.total_size";
    public static final String ATTRIBUTE_BUILD_CHECKOUT_TIME = TRACER_INSTRUMENTATION_NAME + ".build_checkout_time_ms";

    public static final String EVENT_STARTED = "Build Started";
    public static final String EVENT_FINISHED = "Build Finished";

    public static final String EXCEPTION_ERROR_MESSAGE_DURING_BUILD_START = "Error during build start process";
    public static final String EXCEPTION_ERROR_MESSAGE_DURING_BUILD_FINISH = "Error during build finish process";

    public final static String PLUGIN_NAME = "teamcity-opentelemetry";
}

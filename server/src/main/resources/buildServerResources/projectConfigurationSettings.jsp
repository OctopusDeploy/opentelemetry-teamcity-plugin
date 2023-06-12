<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>

<%--magical import that you're just supposed to know about: gives css and js includes--%>
<%@ include file="/include.jsp" %>

<jsp:useBean id="currentProject" type="jetbrains.buildServer.serverSide.SProject" scope="request"/>

<html>
<body>
    <h2>
        OpenTelemetry
    </h2>
    <div class="grayNote">
        Send build trace data to an OpenTelemetry collector, helping you visualize how to optimize your builds and their dependency trees
    </div>

    <div class="editSettingsPage">
        <form id="editOpenTelemetrySettingsPage">
            <table class="runnerFormTable">
                <c:if test='${isInherited || isOverridden}'>
                    <tr>
                        <td colspan="2">
                            <c:if test='${isInherited}'>
                                <i>Inherited from project <c:out value="${inheritedFromProjectName}" /></i>
                            </c:if>
                            <c:if test='${isOverridden}'>
                                <i>Overrides configuration from project <c:out value="${overwritesInheritedFromProjectName}" /></i>
                                <forms:button onclick="BS.ProjectConfigurationSettings.reset()">Reset</forms:button>
                            </c:if>
                        </td>
                    </tr>
                </c:if>
                <tr>
                    <th><label for="enabled">Enabled:&nbsp;</label></th>
                    <td>
                        <forms:checkbox name="enabled" checked="${otelEnabled}" >&nbsp;</forms:checkbox>
                        <span class="error" id="error_enabled"></span>
                    </td>
                </tr>
                <tr>
                    <th><label for="service">Service:&nbsp;<span class="mandatoryAsterix" title="Mandatory field">*</span></label></th>
                    <td>
                        <%-- todo: NOW load options from server side instead of hardcoding--%>
                        <select name="service" id="service" onchange="BS.ProjectConfigurationSettings.serviceChanged(this)">
                            <option value="honeycomb.io" <c:if test='${otelService == "honeycomb.io"}'>selected="selected"</c:if>>Honeycomb.io</option>
                            <option value="zipkin.io" <c:if test='${otelService == "zipkin.io"}'>selected="selected"</c:if>>Zipkin</option>
                            <option value="custom" <c:if test='${otelService == "custom"}'>selected="selected"</c:if>>Custom</option>
                        </select>
                        <span class="error" id="error_service"></span>
                    </td>
                </tr>
                <%-- todo: NOW extract these fields to separate classes--%>
                <tr <c:if test='${otelService == "honeycomb.io"}'>style="display: none"</c:if>>
                    <th><label for="endpoint">Endpoint:&nbsp;<span class="mandatoryAsterix" title="Mandatory field">*</span></label></th>
                    <td>
                        <input type="text" name="endpoint" id="endpoint" value="${otelEndpoint}" class="textField longField">
                        <span class="error" id="error_endpoint"></span>
                    </td>
                </tr>
                <tr <c:if test='${otelService != "honeycomb.io"}'>style="display: none"</c:if>>
                    <th><label for="honeycombApiKey">API Key:&nbsp;<span class="mandatoryAsterix" title="Mandatory field">*</span></label></th>
                    <td>
                        <forms:passwordField className="textField longField" name="honeycombApiKey" id="honeycombApiKey" encryptedPassword="${otelHoneycombApiKey}"/>
                        <span class="error" id="error_honeycombApiKey"></span>
                    </td>
                </tr>
                <tr <c:if test='${otelService != "honeycomb.io"}'>style="display: none"</c:if>>
                    <th><label for="honeycombTeam">Team:&nbsp;<span class="mandatoryAsterix" title="Mandatory field">*</span></label></th>
                    <td>
                        <input type="text" name="honeycombTeam" id="honeycombTeam" value="${otelHoneycombTeam}" class="textField longField">
                        <span class="error" id="error_honeycombTeam"></span>
                    </td>
                </tr>
                <tr <c:if test='${otelService != "honeycomb.io"}'>style="display: none"</c:if>>
                    <th><label for="honeycombDataset">Dataset:&nbsp;<span class="mandatoryAsterix" title="Mandatory field">*</span></label></th>
                    <td>
                        <input type="text" name="honeycombDataset" id="honeycombDataset" value="${otelHoneycombDataset}" class="textField longField">
                        <span class="error" id="error_honeycombDataset"></span>
                    </td>
                </tr>
                <tr id="customHeaders" <c:if test='${otelService != "custom"}'>style="display: none"</c:if>>
                    <th><label>Headers:</label></th>
                    <td>
                        <table class="highlightable parametersTable">
                            <tr style="background-color: #f7f9fa;">
                                <th style="width: 20%">Name</th>
                                <th style="width: 20%">Type</th>
                                <th style="width: 40%">Value</th>
                                <th style="width: 20%">Actions</th>
                            </tr>
                            <c:if test="${not empty otelHeaders}">
                                <c:forEach var="otelHeader" items="${otelHeaders}" varStatus="status">
                                    <tr>
                                        <td>
                                            <%-- todo: consider changing to forms:textfield --%>
                                            <input type="text" name="headerKey_${status.index}" value="${otelHeader.getKey()}" class="textField">
                                        </td>
                                        <td>
                                            <select name="headerType_${status.index}" onchange="BS.ProjectConfigurationSettings.headerTypeChanged(this)">
                                               <option value='plaintext' <c:if test='${otelHeader.getType() == "plaintext"}'>selected="selected"</c:if>>Text</option>
                                               <option value='password' <c:if test='${otelHeader.getType() == "password"}'>selected="selected"</c:if>>Password</option>
                                           </select>
                                        </td>
                                        <td>
                                            <c:if test='${otelHeader.getType() == "plaintext"}'>
                                                <input type="text" name="headerValue_${status.index}" value="${otelHeader.getValue()}" class="textField longField" size="100">
                                            </c:if>
                                            <c:if test='${otelHeader.getType() == "password"}'>
                                                <forms:passwordField className="textField longField" name="headerValue_${status.index}" id="headerValue_${status.index}" encryptedPassword="${otelHeader.getEncryptedValue()}"/>
                                            </c:if>
                                        </td>
                                        <td>
                                            <%-- todo: buttons dont disable on save --%>
                                            <forms:button onclick="BS.ProjectConfigurationSettings.removeHeader(this)">Remove</forms:button>
                                        </td>
                                    </tr>
                                </c:forEach>
                            </c:if>
                            <tr>
                                <td colspan="3">
                                    <forms:addButton onclick="BS.ProjectConfigurationSettings.addHeader(this, ${otelHeaders.size()})">Add Header</forms:addButton>
                                </td>
                            </tr>
                        </table>
                        <span class="error" id="error_headers"></span>
                    </td>
                </tr>
            </table>

            <div class="saveButtonsBlock" id="saveButtons">
                <%-- todo: save progress doesn't seem to work --%>
                <forms:saving id="saveProgress"/>
                <forms:button onclick="BS.ProjectConfigurationSettings.save()">Save</forms:button>
                <input type="hidden" name="projectId" value="${currentProject.externalId}"/>
                <input type="hidden" name="mode" value="save" />
                <input type="hidden" id="publicKey" name="publicKey" value="<c:out value='${publicKey}'/>"/>
            </div>
        </form>
    </div>
<script>
    BS.ProjectConfigurationSettings.serviceChanged($j('#service'));
</script>
</body>
</html>

'use strict';

BS.ProjectConfigurationSettings = OO.extend(BS.PluginPropertiesForm, OO.extend(BS.AbstractPasswordForm, {
    // getContainer: function () {
    //     return $('AgentPriorityDialog');
    // },
    //
    formElement: function () {
        return $('editOpenTelemetrySettingsPage');
    },

    savingIndicator: function () {
        return $('saveProgress');
    },

    addHeader: function(button, index) {
        $j(button).closest('tr').before($j(" <tr>\n" +
                                           "    <td>\n" +
                                           "        <input type=\"text\" name=\"headerKey_" + index + "\" value=\"\" class=\"textField\">\n" +
                                           "    </td>\n" +
                                           "    <td>\n" +
                                           "       <select name=\"headerType_" + index + "\" onchange=\"BS.ProjectConfigurationSettings.headerTypeChanged(this)\">\n" +
                                           "           <option value='plaintext'>Text</option>\n" +
                                           "           <option value='password'>Password</option>\n" +
                                           "       </select>\n" +
                                           "    </td>\n" +
                                           "    <td>\n" +
                                           "        <input type=\"text\" name=\"headerValue_" + index + "\" value=\"\" class=\"textField longField\">\n" +
                                           "    </td>\n" +
                                           "    <td>\n" +
                                           "        <a class=\"btn \" href=\"#\" onclick=\"BS.ProjectConfigurationSettings.removeHeader(this)\">Remove</a>\n" +
                                           "    </td>\n" +
                                           "</tr>"));
        $j(button).attr("onclick", "BS.ProjectConfigurationSettings.addHeader(this, " + (index + 1) + ")");
    },

    removeHeader: function(button) {
        $j(button).closest('tr').remove();
    },

    serviceChanged: function(dropdown) {
        if ($j(dropdown).val() === 'honeycomb.io') {
            $j('#endpoint').val('https://api.honeycomb.io:443');
            $j('#endpoint').closest('tr').hide();
        } else {
            $j('#endpoint').closest('tr').show();
        }
    },

    headerTypeChanged: function(dropdown) {
        const valueField = $j(dropdown).closest('tr').find('input').last();
        if ($j(dropdown).val() === 'plaintext') {
            valueField.attr('type', 'text');
        } else {
            valueField.attr('type', 'password');
        }
    },

    save: function () {
        $j("input[name='mode']").val("save");
        BS.FormSaver.save(this, "/admin/teamcity-opentelemetry/settings.html", OO.extend(BS.ErrorsAwareListener, {
            onCompleteSave: function (form, responseXML, err) {
                err = BS.XMLResponse.processErrors(responseXML, {}, BS.PluginPropertiesForm.propertiesErrorsHandler);
                form.setSaving(false);
                form.enable();
            }
        }));
        return false;
    },

    reset: function () {
        $j("input[name='mode']").val("reset");
        BS.FormSaver.save(this, "/admin/teamcity-opentelemetry/settings.html", OO.extend(BS.ErrorsAwareListener, {
            onCompleteSave: function (form, responseXML, err) {
                err = BS.XMLResponse.processErrors(responseXML, {}, BS.PluginPropertiesForm.propertiesErrorsHandler);
                form.setSaving(false);
                form.enable();
            }
        }));
        return false;
    }
}));

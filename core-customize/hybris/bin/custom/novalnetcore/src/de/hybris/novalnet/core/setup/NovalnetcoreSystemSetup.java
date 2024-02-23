package de.hybris.novalnet.core.setup;

import de.hybris.novalnet.core.constants.NovalnetCoreConstants;
import de.hybris.platform.commerceservices.setup.AbstractSystemSetup;
import de.hybris.platform.core.initialization.SystemSetup;
import de.hybris.platform.core.initialization.SystemSetupContext;
import de.hybris.platform.core.initialization.SystemSetupParameter;
import de.hybris.platform.core.initialization.SystemSetupParameterMethod;

import java.util.ArrayList;
import java.util.List;

@SystemSetup(extension = NovalnetCoreConstants.EXTENSIONNAME)
public class NovalnetcoreSystemSetup extends AbstractSystemSetup {

    private static final String IMPORT_CORE_DATA = "importCoreData";
    private static final String IMPORT_SAMPLE_DATA_ELECTRONICS = "importSampleDataElectronics";
    private static final String IMPORT_SAMPLE_DATA_APPAREL = "importSampleDataApparel";
    private static final String ACTIVATE_PAYMENT_METHOD = "activatePaymentMethod";


    @Override
    @SystemSetupParameterMethod
    public List<SystemSetupParameter> getInitializationOptions() {
        final List<SystemSetupParameter> params = new ArrayList<>();

        params.add(createBooleanSystemSetupParameter(IMPORT_CORE_DATA, "Import Core Data", true));
        
        return params;
    }


    @SystemSetup(type = SystemSetup.Type.PROJECT, process = SystemSetup.Process.ALL)
    public void createProjectData(final SystemSetupContext context) {
        if (this.getBooleanSystemSetupParameter(context, IMPORT_CORE_DATA)) {
            importImpexFile(context, "/novalnetcore/import/AddNovalnetPaymentMode.impex");
        }
    }
}

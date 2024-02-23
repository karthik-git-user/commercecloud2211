package novalnet.controllers.v2;

import novalnet.controllers.NoCheckoutCartException;
import de.hybris.platform.order.InvalidCartException;
import de.hybris.platform.commerceservices.request.mapping.annotation.RequestMappingOverride;
import de.hybris.platform.commerceservices.request.mapping.annotation.ApiVersion;
import de.hybris.platform.commercewebservicescommons.dto.order.PaymentDetailsWsDTO;
import de.hybris.platform.commercewebservicescommons.errors.exceptions.PaymentAuthorizationException;
import de.hybris.platform.commercewebservicescommons.annotation.SiteChannelRestriction;
import de.hybris.platform.webservicescommons.mapping.DataMapper;
import de.hybris.platform.webservicescommons.swagger.ApiFieldsParam;
import de.hybris.platform.webservicescommons.swagger.ApiBaseSiteIdAndUserIdParam;
import de.hybris.platform.store.BaseStoreModel;
import javax.annotation.Resource;

import java.util.Map;

import org.apache.log4j.Logger;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RequestParam;

import org.springframework.http.HttpStatus;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;

import static de.hybris.platform.webservicescommons.mapping.FieldSetLevelHelper.DEFAULT_LEVEL;
import de.hybris.platform.webservicescommons.mapping.FieldSetLevelHelper;
import java.net.URL;

import javax.annotation.Resource;
import java.util.List;
import java.util.Date;
import com.google.gson.Gson;
import java.io.*;

import de.hybris.novalnet.core.model.NovalnetPaymentModeModel;

import de.hybris.platform.core.model.order.payment.PaymentModeModel;
import de.hybris.platform.core.model.order.CartModel;
import de.hybris.platform.order.PaymentModeService;
import de.hybris.platform.commercefacades.order.OrderFacade;
import de.hybris.platform.commercefacades.user.data.CountryData;
import de.hybris.platform.commercefacades.order.data.CartData;
import de.novalnet.order.NovalnetOrderFacade;
import de.novalnet.beans.NnCreditCardData;
import de.novalnet.beans.NnDirectDebitSepaData;
import de.novalnet.beans.NnPayPalData;
import de.novalnet.beans.NnPaymentData;
import de.novalnet.beans.NnConfigData;
import novalnet.dto.payment.NnConfigWsDTO;

import de.novalnet.beans.NnGuaranteedDirectDebitSepaData;
import de.novalnet.beans.NnGuaranteedInvoiceData;
import de.novalnet.beans.NnInvoiceData;
import de.novalnet.beans.NnPrepaymentData;
import de.novalnet.beans.NnBarzahlenData;
import de.novalnet.beans.NnInstantBankTransferData;
import de.novalnet.beans.NnOnlineBankTransferData;
import de.novalnet.beans.NnBancontactData;
import de.novalnet.beans.NnMultibancoData;
import de.novalnet.beans.NnIdealData;
import de.novalnet.beans.NnEpsData;
import de.novalnet.beans.NnGiropayData;
import de.novalnet.beans.NnPrzelewy24Data;
import de.novalnet.beans.NnPostFinanceCardData;
import de.novalnet.beans.NnPostFinanceData;

@Controller
@RequestMapping(value = "/{baseSiteId}/novalnet/config")
@ApiVersion("v2")
@Tag(name = "Novalnet Carts")
public class NovalnetConfigController 
{
    private final static Logger LOG = Logger.getLogger(NovalnetConfigController.class);
    
    protected static final String DEFAULT_FIELD_SET = FieldSetLevelHelper.DEFAULT_LEVEL;
    
    private BaseStoreModel baseStore;
    private CartData cartData;
    private CartModel cartModel;
    private String password;
   
    private static final String PAYMENT_AUTHORIZE = "AUTHORIZE";

    @Resource(name = "novalnetOrderFacade")
    NovalnetOrderFacade novalnetOrderFacade;
    
    @Resource(name = "dataMapper")
    private DataMapper dataMapper;
    
    @Resource
    private PaymentModeService paymentModeService;

    private static final String REQUEST_MAPPING = "paymentType,action,cartId,billingAddress(titleCode,firstName,lastName,line1,line2,town,postalCode,country(isocode),region(isocode)),paymentData(panHash,uniqId,iban),returnUrl,tid";
    
    protected static final String API_COMPATIBILITY_B2C_CHANNELS = "api.compatibility.b2c.channels";

    
    
    @Secured({ "ROLE_CUSTOMERGROUP", "ROLE_CLIENT", "ROLE_CUSTOMERMANAGERGROUP", "ROLE_TRUSTED_CLIENT" })
    @RequestMapping(value = "/details", method = RequestMethod.GET)
    @RequestMappingOverride
    @ResponseStatus(HttpStatus.CREATED)
    @ResponseBody
    @SiteChannelRestriction(allowedSiteChannelsProperty = API_COMPATIBILITY_B2C_CHANNELS)
    @Operation(operationId = "paymentConfig", summary = "return payment configuration", description = "return payment configuration stored in Backend")
    
    @ApiBaseSiteIdAndUserIdParam
    public NnConfigWsDTO getPaymentConfig(
            @ApiFieldsParam @RequestParam(defaultValue = DEFAULT_FIELD_SET) final String fields)
            throws PaymentAuthorizationException, InvalidCartException, NoCheckoutCartException
    {
        final BaseStoreModel baseStore = novalnetOrderFacade.getBaseStoreModel();

        PaymentModeModel novalnetPaymentModeModel         = paymentModeService.getPaymentModeForCode("novalnet");
        NovalnetPaymentModeModel novalnetPaymentMethod    = (NovalnetPaymentModeModel) novalnetPaymentModeModel;
        NnConfigData configData                           = new NnConfigData();
        configData.setNovalnetAccessKey(baseStore.getNovalnetPaymentAccessKey());
        configData.setNovalnetActive(novalnetPaymentMethod.getActive());
        configData.setNovalnetDisplayPayment(baseStore.getNovalnetDisplayPayments());

        return dataMapper.map(configData, NnConfigWsDTO.class, fields);
    }

}

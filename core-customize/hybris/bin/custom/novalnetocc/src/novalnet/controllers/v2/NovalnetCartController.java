package novalnet.controllers.v2;

import novalnet.controllers.NoCheckoutCartException;
import novalnet.controllers.NovalnetPaymentException;
import de.hybris.platform.order.InvalidCartException;
import de.hybris.platform.commercewebservicescommons.dto.order.OrderWsDTO;
import de.hybris.platform.commercewebservicescommons.dto.order.CartWsDTO;
import de.hybris.platform.commercefacades.order.data.OrderData;
import de.hybris.platform.commerceservices.request.mapping.annotation.ApiVersion;
import de.hybris.platform.commercewebservicescommons.dto.order.PaymentDetailsWsDTO;
import de.hybris.platform.commercewebservicescommons.errors.exceptions.PaymentAuthorizationException;
import de.hybris.platform.commercewebservicescommons.annotation.SiteChannelRestriction;
import de.hybris.platform.webservicescommons.swagger.ApiFieldsParam;
import de.hybris.platform.webservicescommons.swagger.ApiBaseSiteIdAndUserIdParam;
import de.hybris.platform.core.model.user.AddressModel;
import de.hybris.platform.store.BaseStoreModel;
import javax.annotation.Resource;

import de.hybris.platform.order.CartService;
import de.hybris.platform.webservicescommons.mapping.DataMapper;

import java.util.HashMap;
import java.util.Map;
import java.math.BigDecimal;

import java.text.SimpleDateFormat;
import java.util.Calendar;

import org.apache.log4j.Logger;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import de.hybris.platform.commerceservices.request.mapping.annotation.RequestMappingOverride;
import de.hybris.platform.webservicescommons.swagger.ApiBaseSiteIdUserIdAndCartIdParam;
import org.springframework.web.bind.annotation.PostMapping;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;

import static de.hybris.platform.webservicescommons.mapping.FieldSetLevelHelper.DEFAULT_LEVEL;
import de.hybris.platform.webservicescommons.mapping.FieldSetLevelHelper;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.servicelayer.search.SearchResult;
import java.net.URL;

import org.json.JSONObject;
import java.net.MalformedURLException;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import javax.annotation.Resource;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Date;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.*;

import de.hybris.novalnet.core.model.NovalnetPaymentInfoModel;
import de.hybris.platform.core.model.order.payment.PaymentInfoModel;
import de.hybris.platform.commerceservices.service.data.CommerceCheckoutParameter;
import de.hybris.platform.commerceservices.order.CommerceCheckoutService;

import de.hybris.novalnet.core.model.NovalnetCallbackInfoModel;
import de.hybris.platform.core.model.order.payment.PaymentModeModel;
import de.hybris.platform.payment.model.PaymentTransactionEntryModel;
import de.hybris.platform.orderhistory.model.OrderHistoryEntryModel;
import de.hybris.platform.core.model.order.CartModel;
import de.hybris.platform.core.model.user.UserModel;
import de.hybris.platform.core.enums.OrderStatus;
import de.hybris.platform.payment.model.PaymentTransactionModel;
import de.hybris.platform.order.PaymentModeService;
import de.hybris.platform.commercefacades.order.OrderFacade;
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery;
import de.hybris.platform.commercefacades.user.data.CountryData;
import de.hybris.platform.commercefacades.order.data.CartData;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.servicelayer.i18n.CommonI18NService;
import de.hybris.platform.jalo.JaloSession;
import de.novalnet.order.NovalnetOrderFacade;
import de.novalnet.beans.NnResponseData;
import de.novalnet.beans.NnPaymentResponseData;
import novalnet.dto.payment.NnPaymentResponseWsDTO;
import de.novalnet.beans.NnPaymentDetailsData;
import novalnet.dto.payment.NnPaymentDetailsWsDTO;
import novalnet.dto.payment.NnRequestWsDTO;
import novalnet.dto.payment.NnCartWsDTO;
import novalnet.dto.payment.NnBillingWsDTO;
import de.novalnet.beans.NnCartData;
import de.novalnet.beans.NnCreditCardData;
import de.novalnet.beans.NnDirectDebitSepaData;
import de.novalnet.beans.NnPayPalData;
import de.novalnet.beans.NnRequestData;
import de.novalnet.beans.NnBillingData;
import de.novalnet.beans.NnCountryData;
import de.novalnet.beans.NnRegionData;
import de.novalnet.beans.NnPaymentData;
import de.novalnet.beans.NnPaymentsData;
import de.novalnet.beans.NnConfigData;
import novalnet.dto.payment.NnConfigWsDTO;
import java.text.NumberFormat;
import java.text.DecimalFormat;

import de.hybris.platform.commercewebservicescommons.dto.order.CardTypeWsDTO;
import de.hybris.platform.commercewebservicescommons.dto.order.OrderEntryWsDTO;
import de.hybris.platform.commercewebservicescommons.dto.order.OrderWsDTO;
import de.hybris.platform.commercewebservicescommons.dto.order.PaymentDetailsWsDTO;
import de.hybris.platform.commercewebservicescommons.dto.product.PriceWsDTO;
import de.hybris.platform.commercewebservicescommons.dto.user.AddressWsDTO;
import de.hybris.platform.commercewebservicescommons.dto.user.CountryWsDTO;
import de.hybris.platform.commercewebservicescommons.dto.user.RegionWsDTO;
import de.hybris.platform.commercewebservicescommons.dto.user.UserWsDTO;


@Controller
@RequestMapping(value = "/{baseSiteId}/novalnet")
@ApiVersion("v2")
@Tag(name = "Novalnet Carts")
public class NovalnetCartController
{
    protected static final String DEFAULT_FIELD_SET = FieldSetLevelHelper.DEFAULT_LEVEL;

    private final static Logger LOG = Logger.getLogger(NovalnetCartController.class);

    private BaseStoreModel baseStore;
    private CartData cartData;
    private CartModel cartModel;
    private String password;
    public String message = "";

    private static final String PAYMENT_AUTHORIZE = "AUTHORIZE";

    @Resource(name = "novalnetOrderFacade")
    NovalnetOrderFacade novalnetOrderFacade;

    @Resource(name = "dataMapper")
    private DataMapper dataMapper;

    @Resource
    private PaymentModeService paymentModeService;

    @Resource
    private CommerceCheckoutService commerceCheckoutService;

    protected static final String API_COMPATIBILITY_B2C_CHANNELS = "api.compatibility.b2c.channels";

    @Secured({ "ROLE_CUSTOMERGROUP", "ROLE_CLIENT", "ROLE_CUSTOMERMANAGERGROUP", "ROLE_TRUSTED_CLIENT" })
    @PostMapping(value = "/getPaymentURL", consumes = { MediaType.APPLICATION_JSON_VALUE })
    @ResponseStatus(HttpStatus.CREATED)
    @ResponseBody
    @Operation(operationId = "getPaymentURL", summary = "Form payment URL with the cart details", description = "Returns the payment URL.")
    @ApiBaseSiteIdUserIdAndCartIdParam
    public NnPaymentResponseWsDTO getPaymentURL(@Parameter(description = "") @RequestBody final NnRequestWsDTO orderRequest,
    @ApiFieldsParam @RequestParam(defaultValue = DEFAULT_FIELD_SET) final String fields) {

        NnRequestData requestData = dataMapper.map(orderRequest, NnRequestData.class, fields);
        cartData = novalnetOrderFacade.loadCart(requestData.getCartId());

        String totalAmount          = formatAmount(String.valueOf(cartData.getTotalPriceWithTax().getValue()));
        DecimalFormat decimalFormat = new DecimalFormat("##.##");
        String orderAmount          = decimalFormat.format(Float.parseFloat(totalAmount));
        float floatAmount           = Float.parseFloat(orderAmount);
        BigDecimal orderAmountCents = BigDecimal.valueOf(floatAmount).multiply(BigDecimal.valueOf(100));
        orderAmountCents = orderAmountCents.setScale(2, BigDecimal.ROUND_HALF_EVEN);
        Integer orderAmountCent     = orderAmountCents.intValue();
        final String currency       = cartData.getTotalPriceWithTax().getCurrencyIso();
        final Locale language       = JaloSession.getCurrentSession().getSessionContext().getLocale();
        final String languageCode   = language.toString().toUpperCase();
        final String emailAddress   = JaloSession.getCurrentSession().getUser().getLogin();

        String requsetDeatils = formPaymentRequest(requestData, emailAddress, orderAmountCent, currency, languageCode);
        StringBuilder response = sendRequest( "https://payport.novalnet.de/v2/seamless/payment", requsetDeatils.toString());
        JSONObject tomJsonObject = new JSONObject(response.toString());
        JSONObject resultJsonObject = tomJsonObject.getJSONObject("result");
        JSONObject transactionJsonObject = tomJsonObject.getJSONObject("transaction");
        String returnData = "";
        NnPaymentResponseWsDTO responseData = new NnPaymentResponseWsDTO();

        if(!String.valueOf("100").equals(resultJsonObject.get("status_code").toString())) {
            returnData = resultJsonObject.get("status_text").toString() != null ? resultJsonObject.get("status_text").toString() : resultJsonObject.get("status_desc").toString();
            responseData.setError(returnData);

        } else {
            returnData = resultJsonObject.get("redirect_url").toString();
            responseData.setPaymentURL(returnData);
        }

        return responseData;
    }

    public String formPaymentRequest(NnRequestData requestData, String emailAddress, Integer orderAmountCent, String currency, String languageCode) {

        final Map<String, Object> transactionParameters = new HashMap<String, Object>();
        final Map<String, Object> merchantParameters    = new HashMap<String, Object>();
        final Map<String, Object> customerParameters    = new HashMap<String, Object>();
        final Map<String, Object> billingParameters     = new HashMap<String, Object>();
        final Map<String, Object> shippingParameters    = new HashMap<String, Object>();
        final Map<String, Object> customParameters      = new HashMap<String, Object>();
        final Map<String, Object> hostedPageParameters     = new HashMap<String, Object>();
        final Map<String, Object> dataParameters        = new HashMap<String, Object>();
        final Map<String, Object> responeParameters     = new HashMap<String, Object>();

        Boolean sameAsDelivery = requestData.getSameAsdelivery();

        NnBillingData billingData =  requestData.getBillingAddress();

        String firstName, lastName, street1, street2, town, zip, countryCode;
        firstName = lastName = street1 = street2 = town = zip = countryCode = "";

        AddressModel billingAddress = novalnetOrderFacade.getBillingAddress(billingData.getAddressId());

        firstName    = billingAddress.getFirstname();
        lastName     = billingAddress.getLastname();
        street1      = billingAddress.getLine1();
        street2      = (billingAddress.getLine2() != null) ? billingAddress.getLine2() : "";
        town         = billingAddress.getTown();
        zip          = billingAddress.getPostalcode();
        countryCode  = billingAddress.getCountry().getIsocode();

        if(sameAsDelivery) {
            customerParameters.put("first_name", firstName);
            customerParameters.put("last_name", lastName);

            billingParameters.put("street", street1 + " " + street2);
            billingParameters.put("city", town);
            billingParameters.put("zip", zip);
            billingParameters.put("country_code", countryCode);

            shippingParameters.put("same_as_billing", 1);

        } else {

            NnCountryData countryData =  billingData.getCountry();

            customerParameters.put("first_name", billingData.getFirstName());
            customerParameters.put("last_name",  billingData.getLastName());

            billingParameters.put("street", billingData.getLine1() + " " + ((billingData.getLine2() != null) ? billingData.getLine2() : ""));
            billingParameters.put("city", billingData.getTown());
            billingParameters.put("zip", billingData.getPostalCode());
            billingParameters.put("country_code", countryData.getIsoCode());

            shippingParameters.put("first_name", firstName);
            shippingParameters.put("last_name", lastName);
            shippingParameters.put("street", street1 + " " + street2);
            shippingParameters.put("city", town);
            shippingParameters.put("zip", zip);
            shippingParameters.put("country_code", countryCode);
        }

        customParameters.put("lang", languageCode);

        transactionParameters.put("currency", currency);
        transactionParameters.put("amount", orderAmountCent);
        transactionParameters.put("system_name", "SAP Commerce Cloud");
        transactionParameters.put("system_version", "2211-NN1.1.1");

        hostedPageParameters.put("type", "PAYMENTFORM");
        hostedPageParameters.put("hide_blocks", new String[] {"ADDRESS_FORM", "SHOP_INFO", "LANGUAGE_MENU", "TARIFF"});
        hostedPageParameters.put("skip_pages", new String[] {"CONFIRMATION_PAGE", "SUCCESS_PAGE"});

        baseStore = novalnetOrderFacade.getBaseStoreModel();
        password = baseStore.getNovalnetPaymentAccessKey().trim();

        merchantParameters.put("signature", baseStore.getNovalnetAPIKey());
        merchantParameters.put("tariff", baseStore.getNovalnetTariffId());

        customerParameters.put("billing", billingParameters);
        customerParameters.put("shipping", shippingParameters);

        dataParameters.put("merchant", merchantParameters);
        dataParameters.put("customer", customerParameters);
        dataParameters.put("transaction", transactionParameters);
        dataParameters.put("custom", customParameters);
        dataParameters.put("hosted_page", hostedPageParameters);

        Gson gson = new GsonBuilder().create();
        String jsonString = gson.toJson(dataParameters);

        return jsonString;

    }

    public StringBuilder sendRequest(String url, String jsonString) {
        StringBuilder response = new StringBuilder();
        try {
            LOG.info("request sent to novalnet");
            LOG.info(jsonString);
            String urly = url;
            URL obj = new URL(urly);
            HttpURLConnection con = (HttpURLConnection) obj.openConnection();
            byte[] postData = jsonString.getBytes(StandardCharsets.UTF_8);
            con.setRequestMethod("POST");
            con.setRequestProperty("Content-Type", "application/json");
            con.setRequestProperty("Charset", "utf-8");
            con.setRequestProperty("Accept", "application/json");
            con.setRequestProperty("X-NN-Access-Key", Base64.getEncoder().encodeToString(password.getBytes()));

            con.setDoOutput(true);
            DataOutputStream wr = new DataOutputStream(con.getOutputStream());
            wr.write(postData);
            wr.flush();
            wr.close();

            int responseCode = con.getResponseCode();
            BufferedReader iny = new BufferedReader(
                    new InputStreamReader(con.getInputStream()));
            String output;


            while ((output = iny.readLine()) != null) {
                response.append(output);
            }
            iny.close();
        } catch (MalformedURLException ex) {
            LOG.error("MalformedURLException ", ex);
        } catch (IOException ex) {
            LOG.error("IOException ", ex);
        }

        LOG.info("response recieved from novalnet");
        LOG.info(response.toString());

        return response;
    }

    public static String formatAmount(String amount) {
        if (amount.contains(",")) {
            try {
                NumberFormat formattedAmount = NumberFormat.getNumberInstance(Locale.GERMANY);
                double formattedValue = formattedAmount.parse(amount).doubleValue();
                amount = Double.toString(formattedValue);
            } catch (Exception e) {
                amount = amount.replace(",", ".");
            }
        }
        return amount;
    }

}

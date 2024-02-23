package novalnet.controllers.v2;

import novalnet.controllers.NoCheckoutCartException;
import novalnet.controllers.NovalnetPaymentException;
import de.hybris.platform.order.InvalidCartException;
import de.hybris.platform.commercewebservicescommons.dto.order.OrderWsDTO;
import de.hybris.platform.commercefacades.order.data.OrderData;
import de.hybris.platform.commerceservices.request.mapping.annotation.ApiVersion;
import de.hybris.platform.commercewebservicescommons.dto.order.PaymentDetailsWsDTO;
import de.hybris.platform.commercewebservicescommons.errors.exceptions.PaymentAuthorizationException;
import de.hybris.platform.commercewebservicescommons.annotation.SiteChannelRestriction;
import de.hybris.platform.webservicescommons.mapping.DataMapper;
import de.hybris.platform.webservicescommons.swagger.ApiFieldsParam;
import de.hybris.platform.webservicescommons.swagger.ApiBaseSiteIdAndUserIdParam;
import de.hybris.platform.core.model.user.AddressModel;
import de.hybris.platform.store.BaseStoreModel;
import javax.annotation.Resource;

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
import java.util.Iterator;

import de.hybris.platform.util.localization.Localization;

import de.hybris.novalnet.core.model.NovalnetPaymentInfoModel;
import de.hybris.novalnet.core.model.NovalnetPaymentModeModel;

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
import novalnet.dto.payment.NnResponseWsDTO;
import de.novalnet.beans.NnPaymentDetailsData;
import novalnet.dto.payment.NnPaymentDetailsWsDTO;
import novalnet.dto.payment.NnRequestWsDTO;
import novalnet.dto.payment.NnPaymentRequestWsDTO;
import novalnet.dto.payment.PayDetailsWsDTO;
import novalnet.dto.payment.NnPaymentsWsDTO;
import de.novalnet.beans.NnCreditCardData;
import de.novalnet.beans.NnDirectDebitSepaData;
import de.novalnet.beans.NnPayPalData;
import de.novalnet.beans.NnRequestData;
import de.novalnet.beans.NnBillingData;
import de.novalnet.beans.NnCountryData;
import de.novalnet.beans.NnRegionData;
import de.novalnet.beans.NnPaymentData;
import de.novalnet.beans.NnPaymentRequestData;
import de.novalnet.beans.PaymentDetailsData;
import de.novalnet.beans.AddressDetailsData;
import de.novalnet.beans.ResultData;
import de.novalnet.beans.PayDetailsData;
import de.novalnet.beans.BookingData;
import de.novalnet.beans.PayDetailsData;
import de.novalnet.beans.NnPaymentsData;
import de.novalnet.beans.NnConfigData;
import novalnet.dto.payment.NnConfigWsDTO;
import java.text.NumberFormat;
import java.text.DecimalFormat;

@Controller
@RequestMapping(value = "/{baseSiteId}/novalnet/orders")
@ApiVersion("v2")
@Tag(name = "Novalnet Carts")
public class NovalnetOrdersController
{
    private final static Logger LOG = Logger.getLogger(NovalnetOrdersController.class);

    protected static final String DEFAULT_FIELD_SET = FieldSetLevelHelper.DEFAULT_LEVEL;

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

    private static final String REQUEST_MAPPING = "paymentType,action,cartId,billingAddress(titleCode,firstName,lastName,line1,line2,town,postalCode,country(isocode),region(isocode)),paymentData(panHash,uniqId,iban),returnUrl,tid";

    protected static final String API_COMPATIBILITY_B2C_CHANNELS = "api.compatibility.b2c.channels";
    @Secured({ "ROLE_CUSTOMERGROUP", "ROLE_CLIENT", "ROLE_CUSTOMERMANAGERGROUP", "ROLE_TRUSTED_CLIENT" })
    @PostMapping(value = "/placeOrder", consumes = { MediaType.APPLICATION_JSON_VALUE,
    MediaType.APPLICATION_XML_VALUE })
    @ResponseStatus(HttpStatus.CREATED)
    @ResponseBody
    @SiteChannelRestriction(allowedSiteChannelsProperty = API_COMPATIBILITY_B2C_CHANNELS)
    @Operation(operationId = "placeOrder", summary = "Place a order.", description = "Authorizes the cart and places the order. The response contains the new order data.")
    @ApiBaseSiteIdAndUserIdParam
    public OrderWsDTO placeOrder(
   @Parameter(description =
    "Request body parameter that contains details such as the name on the card (accountHolderName), the card number (cardNumber), the card type (cardType.code), "
            + "the month of the expiry date (expiryMonth), the year of the expiry date (expiryYear), whether the payment details should be saved (saved), whether the payment details "
            + "should be set as default (defaultPaymentInfo), and the billing address (billingAddress.firstName, billingAddress.lastName, billingAddress.titleCode, billingAddress.country.isocode, "
            + "billingAddress.line1, billingAddress.line2, billingAddress.town, billingAddress.postalCode, billingAddress.region.isocode)\n\nThe DTO is in XML or .json format.", required = true) @RequestBody final NnRequestWsDTO orderRequest,
    @ApiFieldsParam @RequestParam(defaultValue = DEFAULT_FIELD_SET) final String fields)
    throws PaymentAuthorizationException, InvalidCartException, NoCheckoutCartException, NovalnetPaymentException
    {
        NnRequestData requestData = dataMapper.map(orderRequest, NnRequestData.class, fields);
        requestData.getAction();
        cartData = novalnetOrderFacade.loadCart(requestData.getCartId());
        cartModel = novalnetOrderFacade.getCart();
        baseStore = novalnetOrderFacade.getBaseStoreModel();

        String action               = requestData.getAction();
        final String emailAddress   = JaloSession.getCurrentSession().getUser().getLogin();
        String responseString       = "";

        final UserModel currentUser = novalnetOrderFacade.getCurrentUserForCheckout();
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

        password = baseStore.getNovalnetPaymentAccessKey().trim();

        if("create_order".equals(action)) {
            Map<String, Object> requsetDeatils = new HashMap<String, Object>();
            try {
                requsetDeatils = formPaymentRequest(requestData, action, emailAddress, orderAmountCent, currency, languageCode);
            } catch(Exception ex) {
                LOG.error("Novalnet Exception " + message);
                return null;
            }
            StringBuilder response = sendRequest(requsetDeatils.get("paygateURL").toString(), requsetDeatils.get("jsonString").toString());
            responseString = response.toString();
        } else {
            responseString = getTransactionDetails(requestData, languageCode);
        }

        JSONObject tomJsonObject    = new JSONObject(responseString);
        JSONObject resultJsonObject = tomJsonObject.getJSONObject("result");

        if(!String.valueOf("100").equals(resultJsonObject.get("status_code").toString())) {
            final String statMessage = resultJsonObject.get("status_text").toString() != null ? resultJsonObject.get("status_text").toString() : resultJsonObject.get("status_desc").toString();
            LOG.info("Error message recieved from novalnet for cart id: " + requestData.getCartId() + " " + statMessage);
            return null;
        }

        JSONObject transactionJsonObject = tomJsonObject.getJSONObject("transaction");
        String[] successStatus = {"CONFIRMED", "ON_HOLD", "PENDING"};

        if (Arrays.asList(successStatus).contains(transactionJsonObject.get("status").toString())) {

            JSONObject customerJsonObject = tomJsonObject.getJSONObject("customer");
            JSONObject billingJsonObject = customerJsonObject.getJSONObject("billing");

            AddressModel billingAddress =  new AddressModel();

            NnBillingData billingData =  requestData.getBillingAddress();

            String street, town, zip, countryCode;
            street = town = zip = countryCode = "";

            JSONObject customJsonObject = tomJsonObject.getJSONObject("custom");
            billingAddress = novalnetOrderFacade.getBillingAddress(customJsonObject.get("inputval1").toString());


            street = billingAddress.getLine1() + " " + ((billingAddress.getLine2() != null) ? billingAddress.getLine2() : "");
            town         = billingAddress.getTown();
            zip          = billingAddress.getPostalcode();
            countryCode  = billingAddress.getCountry().getIsocode();

            if(!street.equals(billingJsonObject.get("street").toString()) || !zip.equals(billingJsonObject.get("zip").toString()) || !countryCode.equals(billingJsonObject.get("country_code").toString())) {

                billingAddress = novalnetOrderFacade.getModelService().create(AddressModel.class);
                billingAddress.setFirstname(customerJsonObject.get("first_name").toString());
                billingAddress.setLastname(customerJsonObject.get("last_name").toString());
                if (billingJsonObject.has("street")) {
                    billingAddress.setLine1(billingJsonObject.get("street").toString());
                }
                billingAddress.setLine2("");
                billingAddress.setTown(billingJsonObject.get("city").toString());
                billingAddress.setPostalcode(billingJsonObject.get("zip").toString());
                billingAddress.setCountry(novalnetOrderFacade.getCommonI18NService().getCountry(billingJsonObject.get("country_code").toString()));
            }


            billingAddress.setEmail(emailAddress);


            billingAddress.setOwner(cartModel);

            String payment = transactionJsonObject.get("payment_type").toString();

            String paymentname = customJsonObject.get("inputval2").toString();

            OrderData orderData = createOrder(transactionJsonObject, payment, billingAddress, emailAddress, currentUser, orderAmountCent, currency, languageCode, paymentname);
            return dataMapper.map(orderData, OrderWsDTO.class, fields);
        } else {
            final String statMessage = resultJsonObject.get("status_text").toString() != null ? resultJsonObject.get("status_text").toString() : resultJsonObject.get("status_desc").toString();
            LOG.info("Error message recieved from novalnet for cart id: " + requestData.getCartId().toString() + " " + statMessage);
            return null;
        }
    }

    public List<OrderModel> getOrderInfoModel(String orderCode) {
        // Initialize StringBuilder
        StringBuilder query = new StringBuilder();

        // Select query for fetch OrderModel
        query.append("SELECT {pk} from {" + OrderModel._TYPECODE + "} where {" + OrderModel.CODE
                + "} = ?code");
        FlexibleSearchQuery executeQuery = new FlexibleSearchQuery(query.toString());

        // Add query parameter
        executeQuery.addQueryParameter("code", orderCode);

        // Execute query
        SearchResult<OrderModel> result = novalnetOrderFacade.getFlexibleSearchService().search(executeQuery);
        return result.getResult();
    }

    public Map<String, Object> formPaymentRequest(NnRequestData requestData, String action, String emailAddress, Integer orderAmountCent, String currency, String languageCode) throws NovalnetPaymentException {

        baseStore = novalnetOrderFacade.getBaseStoreModel();

        final Map<String, Object> transactionParameters = new HashMap<String, Object>();
        final Map<String, Object> merchantParameters    = new HashMap<String, Object>();
        final Map<String, Object> customerParameters    = new HashMap<String, Object>();
        final Map<String, Object> billingParameters     = new HashMap<String, Object>();
        final Map<String, Object> shippingParameters    = new HashMap<String, Object>();
        final Map<String, Object> customParameters      = new HashMap<String, Object>();
        final Map<String, Object> paymentParameters     = new HashMap<String, Object>();
        final Map<String, Object> dataParameters        = new HashMap<String, Object>();
        final Map<String, Object> responeParameters     = new HashMap<String, Object>();

        AddressDetailsData addressDeatails =  requestData.getAddress();
        NnBillingData billingData =  addressDeatails.getBillingAddress();

        PaymentDetailsData paymentData =  requestData.getPaymentData();
        PayDetailsData payment =  paymentData.getPayment_details();
        BookingData bookingDetails =  paymentData.getBooking_details();
        Boolean sameAsDelivery = addressDeatails.getSameAsdelivery();

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

        Gson gson = new GsonBuilder().create();

        merchantParameters.put("signature", baseStore.getNovalnetAPIKey());
        merchantParameters.put("tariff", baseStore.getNovalnetTariffId());

        customParameters.put("lang", languageCode);
        customParameters.put("input1", "addressId");
        customParameters.put("inputval1", billingData.getAddressId());
        customParameters.put("input2", "payment_name");
        customParameters.put("inputval2", payment.getName());

        transactionParameters.put("payment_type", payment.getType());
        transactionParameters.put("currency", currency);
        transactionParameters.put("amount", orderAmountCent);
        transactionParameters.put("system_name", "SAP Commerce Cloud-OCC & Spartacus");
        transactionParameters.put("system_version", "CXCOMM2211-SPA6.8.0-NN1.1.1");
        transactionParameters.put("test_mode", bookingDetails.getTest_mode());


        if(bookingDetails.getPan_hash() != null) {
            paymentParameters.put("pan_hash", bookingDetails.getPan_hash());
            paymentParameters.put("unique_id", bookingDetails.getUnique_id());
        }

        if(bookingDetails.getWallet_token() != null) {
            paymentParameters.put("wallet_token", bookingDetails.getWallet_token());
        }

        if(bookingDetails.getCreate_token() != null && bookingDetails.getCreate_token() != 0) {
            transactionParameters.put("create_token", bookingDetails.getCreate_token());
        }

        if(bookingDetails.getDue_date() != null) {
            transactionParameters.put("due_date", formatDate(bookingDetails.getDue_date()));
        }

        if(bookingDetails.getIban() != null) {
            paymentParameters.put("iban", bookingDetails.getIban());
            paymentParameters.put("account_holder", bookingDetails.getAccount_holder());
        }

        if(bookingDetails.getBic() != null) {
            paymentParameters.put("bic", bookingDetails.getBic());
        }

        if(bookingDetails.getBirth_date() != null) {
            customerParameters.put("birth_date", bookingDetails.getBirth_date());
        }


        if(requestData.getReturnUrl() != null) {
            transactionParameters.put("return_url", requestData.getReturnUrl());
            transactionParameters.put("error_return_url", requestData.getReturnUrl());
        }

        customerParameters.put("email", emailAddress);
        customerParameters.put("billing", billingParameters);
        customerParameters.put("shipping", shippingParameters);

        transactionParameters.put("payment_data", paymentParameters);
        dataParameters.put("merchant", merchantParameters);
        dataParameters.put("customer", customerParameters);
        dataParameters.put("transaction", transactionParameters);
        dataParameters.put("custom", customParameters);

        String jsonString = gson.toJson(dataParameters);
        String url = (bookingDetails.getPayment_action() != null && (bookingDetails.getPayment_action().toString()).equals("authorized")) ? "https://payport.novalnet.de/v2/authorize" : "https://payport.novalnet.de/v2/payment";

        responeParameters.put("jsonString", jsonString);
        responeParameters.put("paygateURL", url);

        return responeParameters;
    }

    public static String formatDate(int date) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        Calendar calendarInsatance = Calendar.getInstance();
        calendarInsatance.add(calendarInsatance.DATE, date);
        return dateFormat.format(calendarInsatance.getTime());
    }


    public String getTransactionDetails(NnRequestData requestData, String languageCode) {

        Gson gson = new GsonBuilder().create();

        final Map<String, Object> transactionParameters = new HashMap<String, Object>();
        final Map<String, Object> customParameters      = new HashMap<String, Object>();
        final Map<String, Object> dataParameters        = new HashMap<String, Object>();

        transactionParameters.put("tid", requestData.getTid());
        customParameters.put("lang", languageCode);
        dataParameters.put("transaction", transactionParameters);
        dataParameters.put("custom", customParameters);

        String jsonString = gson.toJson(dataParameters);
        String url = "https://payport.novalnet.de/v2/transaction/details";
        StringBuilder response = sendRequest(url, jsonString);
        return response.toString();
    }


    public OrderData createOrder(JSONObject transactionJsonObject, String payment, AddressModel billingAddress, String emailAddress, UserModel currentUser, Integer orderAmountCent, String currency, String languageCode, String paymentname)
    throws InvalidCartException, NoCheckoutCartException {

        PaymentModeModel paymentModeModel = paymentModeService.getPaymentModeForCode("novalnet");

        String testMode = "";

        if(transactionJsonObject.get("test_mode").toString().equals("1")) {
            testMode = Localization.getLocalizedString("novalnet.testOrderText");
        }

        String bankDetails = "";
        String newLine = " <br/> ";
        if(transactionJsonObject.has("bank_details")) {
            bankDetails += "";
            JSONObject bankdeatailsJsonObject = transactionJsonObject.getJSONObject("bank_details");
            bankDetails += newLine + String.format(Localization.getLocalizedString("novalnet.bankDetailsComments1"), cartData.getTotalPriceWithTax().getFormattedValue());
            if (transactionJsonObject.has("due_date")  && !"ON_HOLD".equals(transactionJsonObject.get("status").toString())) {
                bankDetails += " " +  String.format(Localization.getLocalizedString("novalnet.bankDetailsComments2"), transactionJsonObject.get("due_date").toString());
            }
            bankDetails += newLine + Localization.getLocalizedString("novalnet.bankDetailsAccountHolder") + " " + bankdeatailsJsonObject.get("account_holder").toString();
            bankDetails += newLine + Localization.getLocalizedString("novalnet.bankDetailsBank") + " " + bankdeatailsJsonObject.get("bank_name").toString()+ newLine;
            bankDetails += Localization.getLocalizedString("novalnet.bankPlace") + " " + bankdeatailsJsonObject.get("bank_place").toString();
            bankDetails += newLine + Localization.getLocalizedString("novalnet.bankDetailsIban") + " " + bankdeatailsJsonObject.get("iban").toString();
            bankDetails += newLine + Localization.getLocalizedString("novalnet.bankDetailsBic") + " " + bankdeatailsJsonObject.get("bic").toString();

            bankDetails += newLine + Localization.getLocalizedString("novalnet.bankDetailspaymentRefernceMulti") + newLine + Localization.getLocalizedString("novalnet.bankDetailsPaymentReference") + " : TID  " + transactionJsonObject.get("tid").toString() + newLine;
        }

        if(transactionJsonObject.has("partner_payment_reference")) {
            bankDetails += "<br>" + Localization.getLocalizedString("novalnet.multibancocomments1") + " " + cartData.getTotalPriceWithTax().getFormattedValue() + " " + Localization.getLocalizedString("novalnet.multibancocomments2") + "<br>" + Localization.getLocalizedString("novalnet.bankDetailsPaymentReference") + " : " + transactionJsonObject.get("partner_payment_reference").toString();
        }

        if(transactionJsonObject.has("nearest_stores")) {
            JSONObject storeJsonObject = transactionJsonObject.getJSONObject("nearest_stores");
			Iterator<String> keys = storeJsonObject.keys();
			bankDetails += "<br><br>" + "Store(s) near you";
			bankDetails += newLine + "Slip expiry date:" + " " + transactionJsonObject.get("due_date").toString();

			while (keys.hasNext()) {

				String key = keys.next();
				if (storeJsonObject.get(key) instanceof JSONObject) {
					JSONObject nearestStoreJsonObject = storeJsonObject.getJSONObject(key);

					bankDetails += newLine + nearestStoreJsonObject.get("store_name");
					bankDetails += newLine + nearestStoreJsonObject.get("street");
					bankDetails += newLine + nearestStoreJsonObject.get("city");
					bankDetails += newLine + nearestStoreJsonObject.get("zip");
					bankDetails += newLine + nearestStoreJsonObject.get("country_code") + newLine;
				}
			}
        }


        NovalnetPaymentInfoModel paymentInfoModel = new NovalnetPaymentInfoModel();
        paymentInfoModel.setBillingAddress(billingAddress);
        paymentInfoModel.setPaymentEmailAddress(emailAddress);
        paymentInfoModel.setDuplicate(Boolean.FALSE);
        paymentInfoModel.setSaved(Boolean.TRUE);
        paymentInfoModel.setUser(currentUser);
        paymentInfoModel.setPaymentInfo(transactionJsonObject.get("tid").toString());
        paymentInfoModel.setOrderHistoryNotes(Localization.getLocalizedString("novalnet.paymentname")+ " : " +paymentname + newLine + "Novalnet Transaction ID : "+ transactionJsonObject.get("tid").toString() + newLine + testMode + bankDetails);
        paymentInfoModel.setPaymentProvider(payment);
        paymentInfoModel.setPaymentGatewayStatus(transactionJsonObject.get("status").toString());
        cartModel.setPaymentInfo(paymentInfoModel);
        paymentInfoModel.setCode("");

        PaymentTransactionEntryModel orderTransactionEntry = null;
        final List<PaymentTransactionEntryModel> paymentTransactionEntries = new ArrayList<>();
        orderTransactionEntry = novalnetOrderFacade.createTransactionEntry(transactionJsonObject.get("tid").toString(),
                                            cartModel, orderAmountCent, "Novalnet Transaction ID : " + transactionJsonObject.get("tid").toString(), currency);
        paymentTransactionEntries.add(orderTransactionEntry);

        // Initiate/ Update PaymentTransactionModel
        PaymentTransactionModel paymentTransactionModel = new PaymentTransactionModel();
        paymentTransactionModel.setPaymentProvider(payment);
        paymentTransactionModel.setRequestId(transactionJsonObject.get("tid").toString());
        paymentTransactionModel.setEntries(paymentTransactionEntries);
        paymentTransactionModel.setOrder(cartModel);
        paymentTransactionModel.setInfo(paymentInfoModel);

        cartModel.setPaymentTransactions(Arrays.asList(paymentTransactionModel));
        novalnetOrderFacade.getModelService().saveAll(cartModel);

        final OrderData orderData = novalnetOrderFacade.getCheckoutFacade().placeOrder();
        String orderNumber = orderData.getCode();
        List<OrderModel> orderInfoModel = getOrderInfoModel(orderNumber);
        OrderModel orderModel = novalnetOrderFacade.getModelService().get(orderInfoModel.get(0).getPk());

        paymentInfoModel.setCode(orderNumber);
        novalnetOrderFacade.getModelService().save(paymentInfoModel);

        NovalnetPaymentModeModel novalnetPaymentMethod = (NovalnetPaymentModeModel) paymentModeModel;
            orderModel.setPaymentMode(novalnetPaymentMethod);

        orderModel.setStatusInfo(Localization.getLocalizedString("novalnet.paymentname")+ " : " +paymentname+", Novalnet Transaction ID : " + transactionJsonObject.get("tid").toString());

        OrderHistoryEntryModel orderEntry = novalnetOrderFacade.getModelService().create(OrderHistoryEntryModel.class);
        orderEntry.setTimestamp(new Date());
        orderEntry.setOrder(orderModel);
        orderEntry.setDescription("Novalnet Transaction ID : " + transactionJsonObject.get("tid").toString());
        orderModel.setPaymentInfo(paymentInfoModel);
        novalnetOrderFacade.getModelService().saveAll(orderModel, orderEntry);
        novalnetOrderFacade.updateOrderStatus(orderNumber, paymentInfoModel);
        createTransactionUpdate(transactionJsonObject.get("tid").toString(), orderNumber, languageCode);

        long callbackInfoTid = Long.parseLong(transactionJsonObject.get("tid").toString());
        int orderPaidAmount = 0;

        String[] pendingStatusCode = {"PENDING"};

        // Check for payment pending payments
        if (Arrays.asList(pendingStatusCode).contains(transactionJsonObject.get("status").toString())) {
            orderPaidAmount = 0;
        } else {
            orderPaidAmount = orderAmountCent;
        }

        NovalnetCallbackInfoModel novalnetCallbackInfo = new NovalnetCallbackInfoModel();
        novalnetCallbackInfo.setPaymentType(payment);
        novalnetCallbackInfo.setOrderAmount(orderAmountCent);
        novalnetCallbackInfo.setCallbackTid(callbackInfoTid);
        novalnetCallbackInfo.setOrginalTid(callbackInfoTid);
        novalnetCallbackInfo.setPaidAmount(orderPaidAmount);
        novalnetCallbackInfo.setOrderNo(orderNumber);
        novalnetOrderFacade.getModelService().save(novalnetCallbackInfo);

        return orderData;
    }

    public void createTransactionUpdate(String tid, String orderNumber, String languageCode) {

        Gson gson = new GsonBuilder().create();

        final Map<String, Object> transactionParameters = new HashMap<String, Object>();
        final Map<String, Object> customParameters      = new HashMap<String, Object>();
        final Map<String, Object> dataParameters        = new HashMap<String, Object>();

        transactionParameters.put("tid", tid);
        transactionParameters.put("order_no", orderNumber);
        customParameters.put("lang", languageCode);
        dataParameters.put("transaction", transactionParameters);
        dataParameters.put("custom", customParameters);
        String jsonString = gson.toJson(dataParameters);
        String url = "https://payport.novalnet.de/v2/transaction/update";
        StringBuilder response = sendRequest(url, jsonString);
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

    @Secured({ "ROLE_CUSTOMERGROUP", "ROLE_CLIENT", "ROLE_CUSTOMERMANAGERGROUP", "ROLE_TRUSTED_CLIENT" })
    @PostMapping(value = "/getRedirectURL", consumes = { MediaType.APPLICATION_JSON_VALUE,
    MediaType.APPLICATION_XML_VALUE })
    @ResponseStatus(HttpStatus.CREATED)
    @ResponseBody
    @SiteChannelRestriction(allowedSiteChannelsProperty = API_COMPATIBILITY_B2C_CHANNELS)
    @Operation(operationId = "redirecturl", summary = "redirect url", description = "Get the third party url for customer to pay")
    @ApiBaseSiteIdAndUserIdParam
    public NnResponseWsDTO getRedirectURL(
            @Parameter(description =
            "Request body parameter that contains details such as the name on the card (accountHolderName), the card number (cardNumber), the card type (cardType.code), "
                    + "the month of the expiry date (expiryMonth), the year of the expiry date (expiryYear), whether the payment details should be saved (saved), whether the payment details "
                    + "should be set as default (defaultPaymentInfo), and the billing address (billingAddress.firstName, billingAddress.lastName, billingAddress.titleCode, billingAddress.country.isocode, "
                    + "billingAddress.line1, billingAddress.line2, billingAddress.town, billingAddress.postalCode, billingAddress.region.isocode)\n\nThe DTO is in XML or .json format.", required = true) @RequestBody final NnRequestWsDTO orderRequest,
            @ApiFieldsParam @RequestParam(defaultValue = DEFAULT_FIELD_SET) final String fields)
            throws PaymentAuthorizationException, InvalidCartException, NoCheckoutCartException, NovalnetPaymentException
    {
        NnRequestData requestData = dataMapper.map(orderRequest, NnRequestData.class, fields);
        cartData  = novalnetOrderFacade.loadCart(requestData.getCartId());
        cartModel = novalnetOrderFacade.getCart();
        baseStore = novalnetOrderFacade.getBaseStoreModel();

        String action = "get_redirect_url";
        final String emailAddress   = JaloSession.getCurrentSession().getUser().getLogin();
        String responseString = "";

        final UserModel currentUser = novalnetOrderFacade.getCurrentUserForCheckout();
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

        password = baseStore.getNovalnetPaymentAccessKey().trim();
        Map<String, Object> requsetDeatils = new HashMap<String, Object>();
        try {
            requsetDeatils = formPaymentRequest(requestData, action, emailAddress, orderAmountCent, currency, languageCode);
        } catch(Exception ex) {
            LOG.error("error " + ex);
            LOG.error("Novalnet Exception " + message);
            return null;
        }

         StringBuilder response = sendRequest(requsetDeatils.get("paygateURL").toString(), requsetDeatils.get("jsonString").toString());
        JSONObject tomJsonObject = new JSONObject(response.toString());
        JSONObject resultJsonObject = tomJsonObject.getJSONObject("result");
        JSONObject transactionJsonObject = tomJsonObject.getJSONObject("transaction");

        if(!String.valueOf("100").equals(resultJsonObject.get("status_code").toString())) {
            final String statMessage = resultJsonObject.get("status_text").toString() != null ? resultJsonObject.get("status_text").toString() : resultJsonObject.get("status_desc").toString();
            return null;
        }

        String redirectURL = resultJsonObject.get("redirect_url").toString();
        NnResponseData responseData = new NnResponseData();
        responseData.setRedirectURL(redirectURL);
        return dataMapper.map(responseData, NnResponseWsDTO.class, fields);
    }

    @Secured({ "ROLE_CUSTOMERGROUP", "ROLE_CLIENT", "ROLE_CUSTOMERMANAGERGROUP", "ROLE_TRUSTED_CLIENT" })
    @RequestMapping(value = "/paymentDetails", method = RequestMethod.POST)
    @ResponseStatus(HttpStatus.CREATED)
    @ResponseBody
    @SiteChannelRestriction(allowedSiteChannelsProperty = API_COMPATIBILITY_B2C_CHANNELS)
    @Operation(operationId = "paymnetdeatils", summary = "Payment Details.", description = "Payment details for the order")
    @ApiBaseSiteIdAndUserIdParam
    public NnPaymentDetailsWsDTO getPaymentDetails(
            @Parameter(description = "order no", required = true) @RequestParam final String orderno,
            @ApiFieldsParam @RequestParam(defaultValue = DEFAULT_FIELD_SET) final String fields)
            throws PaymentAuthorizationException, InvalidCartException, NoCheckoutCartException
    {
        final List<NovalnetPaymentInfoModel> paymentInfo = novalnetOrderFacade.getNovalnetPaymentInfo(orderno);
        NovalnetPaymentInfoModel paymentInfoModel = novalnetOrderFacade.getPaymentModel(paymentInfo);
        NnPaymentDetailsData paymentDetailsData = new NnPaymentDetailsData();
        paymentDetailsData.setStatus(paymentInfoModel.getPaymentGatewayStatus());
        paymentDetailsData.setComments(paymentInfoModel.getOrderHistoryNotes());
        paymentDetailsData.setTid(paymentInfoModel.getPaymentInfo().toString());
        return dataMapper.map(paymentDetailsData, NnPaymentDetailsWsDTO.class, fields);
    }

}

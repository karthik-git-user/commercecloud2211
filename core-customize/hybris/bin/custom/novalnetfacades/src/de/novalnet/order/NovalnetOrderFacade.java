package de.novalnet.order;

import java.util.List;
import java.util.Date;
import java.util.Arrays;

import de.hybris.platform.core.PK;
import de.hybris.platform.commercefacades.order.data.CCPaymentInfoData;
import de.hybris.platform.commercewebservicescommons.dto.order.PaymentDetailsWsDTO;
import de.hybris.platform.commerceservices.request.mapping.annotation.RequestMappingOverride;
import de.hybris.platform.webservicescommons.util.YSanitizer;
import javax.annotation.Resource;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;


import de.hybris.platform.commercewebservicescommons.errors.exceptions.RequestParameterException;
import de.hybris.platform.payment.enums.PaymentTransactionType;
import de.hybris.platform.payment.dto.TransactionStatus;
import de.hybris.platform.payment.model.PaymentTransactionEntryModel;
import de.hybris.platform.payment.model.PaymentTransactionModel;
import de.hybris.platform.commercefacades.order.CartFacade;
import de.hybris.platform.commercefacades.i18n.I18NFacade;
import de.hybris.platform.commercefacades.i18n.comparators.CountryComparator;
import de.hybris.platform.commercefacades.order.CheckoutFacade;
import de.hybris.platform.commercefacades.user.UserFacade;
import de.hybris.platform.commercefacades.order.OrderFacade;
import de.hybris.platform.commercefacades.order.data.CartData;
import de.hybris.platform.commercefacades.order.data.OrderData;
import de.hybris.platform.commercefacades.user.data.AddressData;
import de.hybris.platform.commercefacades.user.data.CountryData;
import de.hybris.platform.commercefacades.user.data.RegionData;
import de.hybris.platform.commerceservices.strategies.CheckoutCustomerStrategy;
import de.hybris.platform.commercewebservicescommons.strategies.CartLoaderStrategy;
import de.hybris.platform.commerceservices.customer.CustomerAccountService;
import de.hybris.platform.commerceservices.order.CommerceCheckoutService;
import de.hybris.platform.commerceservices.service.data.CommerceCheckoutParameter;
import de.hybris.platform.commercewebservicescommons.dto.order.PaymentDetailsListWsDTO;
import de.hybris.platform.commercewebservicescommons.dto.order.PaymentDetailsWsDTO;
import de.hybris.platform.orderhistory.model.OrderHistoryEntryModel;
import de.hybris.platform.converters.Populator;
import de.hybris.platform.core.enums.OrderStatus;
import de.hybris.platform.core.enums.PaymentStatus;
import de.hybris.platform.core.model.c2l.CountryModel;
import de.hybris.platform.core.model.order.AbstractOrderEntryModel;
import de.hybris.platform.core.model.order.CartModel;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.core.model.order.payment.PaymentInfoModel;
import de.hybris.platform.core.model.user.AddressModel;
import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.core.model.c2l.CurrencyModel;
import de.hybris.platform.core.model.user.TitleModel;
import de.hybris.novalnet.core.model.NovalnetCallbackInfoModel;
import de.hybris.novalnet.core.model.NovalnetPaymentInfoModel;
import de.hybris.platform.order.CalculationService;
import de.hybris.platform.order.CartFactory;
import de.hybris.platform.order.PaymentModeService;
import de.hybris.platform.order.CartService;
import de.hybris.platform.order.InvalidCartException;
import de.hybris.platform.order.exceptions.CalculationException;
import de.hybris.platform.servicelayer.config.ConfigurationService;
import de.hybris.platform.servicelayer.dto.converter.Converter;
import de.hybris.platform.servicelayer.i18n.CommonI18NService;
import de.hybris.platform.servicelayer.keygenerator.KeyGenerator;
import de.hybris.platform.servicelayer.model.ModelService;
import de.hybris.platform.servicelayer.search.FlexibleSearchService;
import de.hybris.platform.servicelayer.search.FlexibleSearchQuery;
import de.hybris.platform.servicelayer.search.SearchResult;

import de.hybris.platform.servicelayer.session.SessionService;
import de.hybris.platform.store.BaseStoreModel;
import de.hybris.platform.store.services.BaseStoreService;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.ui.Model;
import org.springframework.util.CollectionUtils;
import org.springframework.validation.BindingResult;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import java.text.SimpleDateFormat;
import java.text.ParseException;

import de.hybris.novalnet.core.model.NovalnetPaymentInfoModel;
import de.hybris.novalnet.core.model.NovalnetPaymentRefInfoModel;
import de.hybris.platform.core.model.order.payment.PaymentModeModel;
import de.hybris.novalnet.core.model.NovalnetCallbackInfoModel;

import de.novalnet.beans.NnPaymentDetailsData;
import de.novalnet.beans.NnCreditCardData;
import de.novalnet.beans.NnDirectDebitSepaData;
import de.novalnet.beans.NnPayPalData;
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
import de.novalnet.beans.NnPaymentData;
import de.novalnet.beans.NnPrzelewy24Data;
import de.novalnet.beans.NnPostFinanceCardData;
import de.novalnet.beans.NnPostFinanceData;
import de.novalnet.beans.NnConfigData;

/**
 * Facade for setting shipping options on marketplace order entries
 */
public class NovalnetOrderFacade {

    private final static Logger LOG = Logger.getLogger(NovalnetOrderFacade.class);

    private BaseStoreService baseStoreService;
    private SessionService sessionService;
    private CartService cartService;
    private OrderFacade orderFacade;
    private CheckoutFacade checkoutFacade;
    private CheckoutCustomerStrategy checkoutCustomerStrategy;
    private ModelService modelService;
    private FlexibleSearchService flexibleSearchService;
    private CommerceCheckoutService commerceCheckoutService;
    private Converter<AddressData, AddressModel> addressReverseConverter;
    private Converter<CountryModel, CountryData> countryConverter;
    private Converter<OrderModel, OrderData> orderConverter;
    private CartFactory cartFactory;
    private CalculationService calculationService;
    private Populator<AddressModel, AddressData> addressPopulator;
    private CommonI18NService commonI18NService;
    private CustomerAccountService customerAccountService;

    public static final int DAYS_IN_A_YEAR = 365;
    public static final int TOTAL_HOURS = 24;
    public static final int TOTAL_MINUTES_SECONDS = 60;
    public static final int AGE_REQUIREMENT = 18;

    @Resource(name = "i18NFacade")
    private I18NFacade i18NFacade;

    @Resource(name = "cartLoaderStrategy")
    private CartLoaderStrategy cartLoaderStrategy;

    @Resource(name = "userFacade")
    private UserFacade userFacade;
    
    @Resource(name = "commerceWebServicesCartFacade2")
	private CartFacade cartFacade;

    @Resource
    private PaymentModeService paymentModeService;

    public static final String ADDRESS_DOES_NOT_EXIST = "Address with given id: '%s' doesn't exist or belong to another user";
    private static final String OBJECT_NAME_ADDRESS_ID = "addressId";

    public BaseStoreModel getBaseStoreModel() {
        return getBaseStoreService().getCurrentBaseStore();
    }

    public BaseStoreService getBaseStoreService() {
        return baseStoreService;
    }

    public void setBaseStoreService(BaseStoreService baseStoreService) {
        this.baseStoreService = baseStoreService;
    }

    public SessionService getSessionService() {
        return sessionService;
    }

    public void setSessionService(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    public CartService getCartService() {
        return cartService;
    }

    public void setCartService(CartService cartService) {
        this.cartService = cartService;
    }

    public OrderFacade getOrderFacade() {
        return orderFacade;
    }

    public void setOrderFacade(OrderFacade orderFacade) {
        this.orderFacade = orderFacade;
    }

    public CheckoutFacade getCheckoutFacade() {
        return checkoutFacade;
    }

    public void setCheckoutFacade(CheckoutFacade checkoutFacade) {
        this.checkoutFacade = checkoutFacade;
    }

    public CheckoutCustomerStrategy getCheckoutCustomerStrategy() {
        return checkoutCustomerStrategy;
    }

    public void setCheckoutCustomerStrategy(CheckoutCustomerStrategy checkoutCustomerStrategy) {
        this.checkoutCustomerStrategy = checkoutCustomerStrategy;
    }

    public ModelService getModelService() {
        return modelService;
    }

    public void setModelService(ModelService modelService) {
        this.modelService = modelService;
    }

    public CommonI18NService getCommonI18NService() {
        return commonI18NService;
    }

    public void setCommonI18NService(CommonI18NService commonI18NService) {
        this.commonI18NService = commonI18NService;
    }

    public FlexibleSearchService getFlexibleSearchService() {
        return flexibleSearchService;
    }

    public void setFlexibleSearchService(FlexibleSearchService flexibleSearchService) {
        this.flexibleSearchService = flexibleSearchService;
    }

    public Converter<AddressData, AddressModel> getAddressReverseConverter() {
        return addressReverseConverter;
    }

    public void setAddressReverseConverter(Converter<AddressData, AddressModel> addressReverseConverter) {
        this.addressReverseConverter = addressReverseConverter;
    }

    public I18NFacade getI18NFacade() {
        return i18NFacade;
    }

    public void setI18NFacade(I18NFacade i18NFacade) {
        this.i18NFacade = i18NFacade;
    }

    protected Converter<CountryModel, CountryData> getCountryConverter() {
        return countryConverter;
    }

    @Required
    public void setCountryConverter(final Converter<CountryModel, CountryData> countryConverter) {
        this.countryConverter = countryConverter;
    }

    public Converter<OrderModel, OrderData> getOrderConverter() {
        return orderConverter;
    }

    public void setOrderConverter(Converter<OrderModel, OrderData> orderConverter) {
        this.orderConverter = orderConverter;
    }

    public CartFactory getCartFactory() {
        return cartFactory;
    }

    public void setCartFactory(CartFactory cartFactory) {
        this.cartFactory = cartFactory;
    }

    public CalculationService getCalculationService() {
        return calculationService;
    }

    public void setCalculationService(CalculationService calculationService) {
        this.calculationService = calculationService;
    }

    public Populator<AddressModel, AddressData> getAddressPopulator() {
        return addressPopulator;
    }

    /**
     * Set address populator
     *
     * @param addressPopulator
     */
    public void setAddressPopulator(Populator<AddressModel, AddressData> addressPopulator) {
        this.addressPopulator = addressPopulator;
    }

    public void addPaymentDetailsInternal(final NovalnetPaymentInfoModel paymentInfo)
    {
        final CustomerModel currentCustomer = getCurrentUserForCheckout();
        getCustomerAccountService().setDefaultPaymentInfo(currentCustomer, paymentInfo);
        final CartModel cartModel = getCart();
        modelService.save(paymentInfo);
        cartModel.setPaymentInfo(paymentInfo);
        modelService.save(cartModel);
    }

    /**
     * Get Payment transaction entry
     *
     * @param requestId
     * @param cartModel
     * @param amount
     * @param backendTransactionComments
     * @param currencyCode
     */
    public PaymentTransactionEntryModel createTransactionEntry(final String requestId, final CartModel cartModel, final int amount, String backendTransactionComments, String currencyCode) {
        final PaymentTransactionEntryModel paymentTransactionEntry = getModelService().create(PaymentTransactionEntryModel.class);
        paymentTransactionEntry.setRequestId(requestId);
        paymentTransactionEntry.setType(PaymentTransactionType.AUTHORIZATION);
        paymentTransactionEntry.setTransactionStatus(TransactionStatus.ACCEPTED.name());
        paymentTransactionEntry.setTransactionStatusDetails(backendTransactionComments);
        paymentTransactionEntry.setCode(cartModel.getCode());

        final CurrencyModel currency = getCurrencyForIsoCode(currencyCode);
        paymentTransactionEntry.setCurrency(currency);

        final BigDecimal transactionAmount = BigDecimal.valueOf(amount / 100);
        paymentTransactionEntry.setAmount(transactionAmount);
        paymentTransactionEntry.setTime(new Date());

        return paymentTransactionEntry;
    }

    private CurrencyModel getCurrencyForIsoCode(final String currencyIsoCode) {
        CurrencyModel currencyModel = new CurrencyModel();
        currencyModel.setIsocode(currencyIsoCode);
        currencyModel = getFlexibleSearchService().getModelByExample(currencyModel);
        return currencyModel;
    }

    public CustomerModel getCurrentUserForCheckout()
    {
        return getCheckoutCustomerStrategy().getCurrentUserForCheckout();
    }

    protected CommerceCheckoutParameter createCommerceCheckoutParameter(final CartModel cart, final boolean enableHooks)
    {
        final CommerceCheckoutParameter parameter = new CommerceCheckoutParameter();
        parameter.setEnableHooks(enableHooks);
        parameter.setCart(cart);
        return parameter;
    }

    protected CustomerAccountService getCustomerAccountService()
    {
        return customerAccountService;
    }

    @Required
    public void setCustomerAccountService(final CustomerAccountService customerAccountService)
    {
        this.customerAccountService = customerAccountService;
    }

    public boolean hasCheckoutCart()
    {
        return getCartFacade().hasSessionCart();
    }

    public CartModel getCart()
    {
        return hasCheckoutCart() ? getCartService().getSessionCart() : null;
    }

    public CartFacade getCartFacade()
    {
        return cartFacade;
    }

    @Required
    public void setCartFacade(final CartFacade cartFacade)
    {
        this.cartFacade = cartFacade;
    }

    public CartData getSessionCart()
    {
        return cartFacade.getSessionCart();
    }

    protected CommerceCheckoutService getCommerceCheckoutService()
    {
        return commerceCheckoutService;
    }

    @Required
    public void setCommerceCheckoutService(final CommerceCheckoutService commerceCheckoutService)
    {
        this.commerceCheckoutService = commerceCheckoutService;
    }

    public CartData loadCart(final String cartId) {
        cartLoaderStrategy.loadCart(cartId);
        final CartData cartData = getSessionCart();
        return cartData;
    }

    public AddressModel createBillingAddress(String addressId) {

        final AddressModel billingAddress = getModelService().create(AddressModel.class);
        billingAddress.setFirstname("");
        billingAddress.setLastname("");
        billingAddress.setLine1("");
        billingAddress.setLine2("");
        billingAddress.setTown("");
        billingAddress.setPostalcode("");
        billingAddress.setCountry(getCommonI18NService().getCountry("DE"));

        final AddressData addressData = getAddressData(addressId);

        getAddressReverseConverter().convert(addressData, billingAddress);

        return billingAddress;
    }

    /**
     * retrieve address data
     *
     * @param addressId id of the address
     * @return addressData
     */
    public AddressData getAddressData(final String addressId)
    {
        final AddressData addressData = getUserFacade().getAddressForCode(addressId);
        if (addressData == null)
        {
            throw new RequestParameterException(String.format(ADDRESS_DOES_NOT_EXIST, sanitize(addressId)),
                    RequestParameterException.INVALID, OBJECT_NAME_ADDRESS_ID);
        }
        return addressData;
    }

    /**
     * retrieve user facade
     *
     * @param input string
     * @return userFacade
     */
    protected UserFacade getUserFacade()
    {
        return userFacade;
    }

    /**
     * sanitize strinf
     *
     * @param input string
     * @return String
     */
    protected static String sanitize(final String input)
    {
        return YSanitizer.sanitize(input);
    }

     /**
     * Update Order comments
     *
     * @param comments  Formed comments
     * @param orderCode Order code of the order
     * @param transactionStatus transaction status for the order
     */
    public void updateCallbackComments(String comments, String orderCode, String transactionStatus) {
        List<NovalnetPaymentInfoModel> paymentInfo = getNovalnetPaymentInfo(orderCode);

        // Update NovalnetPaymentInfo Order entry notes
        NovalnetPaymentInfoModel paymentInfoModel = this.getModelService().get(paymentInfo.get(0).getPk());
        String previousComments = paymentInfoModel.getOrderHistoryNotes();
        paymentInfoModel.setOrderHistoryNotes(previousComments + "<br><br>" + comments);
        paymentInfoModel.setPaymentGatewayStatus(transactionStatus);
        List<OrderModel> orderInfoModel = getOrderInfoModel(orderCode);

        // Update OrderHistoryEntries
        OrderModel orderModel = this.getModelService().get(orderInfoModel.get(0).getPk());
        OrderHistoryEntryModel orderEntry = this.getModelService().create(OrderHistoryEntryModel.class);
        orderEntry.setTimestamp(new Date());
        orderEntry.setOrder(orderModel);
        orderEntry.setDescription(comments);

        // Save the updated models
        this.getModelService().saveAll(paymentInfoModel, orderEntry);
    }

    /**
     * Get order model
     *
     * @param orderCode Order code of the order
     * @return SearchResult
     */
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
        SearchResult<OrderModel> result = getFlexibleSearchService().search(executeQuery);
        return result.getResult();
    }

    /**
     * Update OrderStatus of the order
     *
     * @param orderCode Order code of the order
     */
    public void updatePartPaidStatus(String orderCode) {
        List<OrderModel> orderInfoModel = getOrderInfoModel(orderCode);

        // Update Part paid status
        OrderModel orderModel = this.getModelService().get(orderInfoModel.get(0).getPk());
        orderModel.setPaymentStatus(PaymentStatus.PARTPAID);

        this.getModelService().save(orderModel);
    }

    /**
     * Update callback info model
     *
     * @param callbackTid     Transaction Id of the executed callback
     * @param orderReference  Order reference list
     * @param orderPaidAmount Total paid amount
     */
    public void updateCallbackInfo(long callbackTid, List<NovalnetCallbackInfoModel> orderReference, int orderPaidAmount) {
        NovalnetCallbackInfoModel callbackInfoModel = this.getModelService().get(orderReference.get(0).getPk());

        // Update Callback TID
        callbackInfoModel.setCallbackTid(callbackTid);

        // Update Paid amount
        callbackInfoModel.setPaidAmount(orderPaidAmount);

        // Save the updated model
        this.getModelService().save(callbackInfoModel);
    }

    /**
     * Get Novalnet payment info model
     *
     * @param orderCode Order code of the order
     * @return SearchResult
     */
    public List<NovalnetPaymentInfoModel> getNovalnetPaymentInfo(String orderCode) {

        // Initialize StringBuilder
        StringBuilder query = new StringBuilder();

        // Select query for fetch NovalnetPaymentInfoModel
        query.append("SELECT {pk} from {PaymentInfo} where {" + PaymentInfoModel.CODE
                + "} = ?code AND {" + PaymentInfoModel.DUPLICATE + "} = ?duplicate");
        FlexibleSearchQuery executeQuery = new FlexibleSearchQuery(query.toString());

        // Add query parameter
        executeQuery.addQueryParameter("code", orderCode);
        executeQuery.addQueryParameter("duplicate", Boolean.FALSE);

        // Execute query
        SearchResult<NovalnetPaymentInfoModel> result = getFlexibleSearchService().search(executeQuery);
        return result.getResult();

    }

    /**
     * Get Payment model
     *
     * @param paymentInfo info of the payment
     * @return paymentModel
     */
    public NovalnetPaymentInfoModel getPaymentModel(final List<NovalnetPaymentInfoModel> paymentInfo) {
        final NovalnetPaymentInfoModel paymentModel = this.getModelService().get(paymentInfo.get(0).getPk());
        return paymentModel;
    }

    /**
     * Update order status
     *
     * @param orderCode Order code of the order
     * @param paymentInfoModel payment configurations
     */
    public void updateOrderStatus(String orderCode, NovalnetPaymentInfoModel paymentInfoModel) {
        List<OrderModel> orderInfoModel = getOrderInfoModel(orderCode);

        OrderModel orderModel = this.getModelService().get(orderInfoModel.get(0).getPk());        
        
        if ("PENDING".equals(paymentInfoModel.getPaymentGatewayStatus())) {
            orderModel.setStatus(OrderStatus.PAYMENT_NOT_CAPTURED);
        }else if ("ON_HOLD".equals(paymentInfoModel.getPaymentGatewayStatus())) {
            orderModel.setStatus( OrderStatus.PAYMENT_AUTHORIZED);
        } else {
			orderModel.setStatus(OrderStatus.COMPLETED);
		}

        final String paymentMethod = paymentInfoModel.getPaymentProvider();
        
        String[] pendingStatusCode = {"ON_HOLD","PENDING"};

        // Check for payment pending payments
        if(Arrays.asList(pendingStatusCode).contains(paymentInfoModel.getPaymentGatewayStatus()))
        {
            orderModel.setPaymentStatus(PaymentStatus.NOTPAID);
        }
        else
        {
            // Update the payment status for completed payments
            orderModel.setPaymentStatus(PaymentStatus.PAID);
        }

        this.getModelService().save(orderModel);

    }

    /**
     * Update Cancel order status
     *
     * @param orderCode Order code of the order
     */
    public void updateCancelStatus(String orderCode) {
        List<OrderModel> orderInfoModel = getOrderInfoModel(orderCode);

        // Update OrderHistoryEntries
        OrderModel orderModel = this.getModelService().get(orderInfoModel.get(0).getPk());

        final BaseStoreModel baseStore = this.getBaseStoreModel();
        OrderStatus orderStatus = OrderStatus.CANCELLED;

        orderModel.setStatus(orderStatus);

        this.getModelService().save(orderModel);

    }

    /**
     * Update Payment info details in database
     *
     * @param orderReference order details
     * @param tidStatus status of the transacction
     */
    public void updatePaymentInfo(List<NovalnetPaymentInfoModel> orderReference, String tidStatus) {
        NovalnetPaymentInfoModel paymentInfoModel = this.getModelService().get(orderReference.get(0).getPk());

        // Update Callback TID
        paymentInfoModel.setPaymentGatewayStatus(tidStatus);

        // Save the updated model
        this.getModelService().save(paymentInfoModel);
    }

    /**
     * Check age requirement
     *
     * @param dateInString dob of the customer
     */
    public static boolean hasAgeRequirement(String dateInString) {

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-mm-dd");
        try {
            Date birthDate = sdf.parse(dateInString);

            long ageInMillis = System.currentTimeMillis() - birthDate.getTime();

            long years = ageInMillis / (DAYS_IN_A_YEAR * TOTAL_HOURS * TOTAL_MINUTES_SECONDS * TOTAL_MINUTES_SECONDS * 1000l);

            if (years >= AGE_REQUIREMENT) {
                return true;
            }
            return false;
        } catch (ParseException e) {
            return false;
        }
    }

    /**
     * Get callback info model
     *
     * @param transactionId Transaction ID of the order
     * @return SearchResult
     */
    public List<NovalnetCallbackInfoModel> getCallbackInfo(String transactionId) {
        // Initialize StringBuilder
        StringBuilder query = new StringBuilder();

        // Select query for fetch NovalnetCallbackInfoModel
        query.append("SELECT {pk} from {" + NovalnetCallbackInfoModel._TYPECODE + "} where {" + NovalnetCallbackInfoModel.ORGINALTID
                + "} = ?transctionId");
        FlexibleSearchQuery executeQuery = new FlexibleSearchQuery(query.toString());

        // Add query parameter
        executeQuery.addQueryParameter("transctionId", transactionId);

        // Execute query
        SearchResult<NovalnetCallbackInfoModel> result = getFlexibleSearchService().search(executeQuery);
        return result.getResult();
    }

    /**
     * Returns the default billing address for the given customer.
     *
     * @param billingAddressPk
     *           the customer's unique ID
     * @return the default billing address of the user
     */
    public AddressModel getBillingAddress(final String billingAddressPk)
    {
        return "0".equals(billingAddressPk) ? null : (AddressModel) getModelService().get(PK.parse(billingAddressPk));
    }

}

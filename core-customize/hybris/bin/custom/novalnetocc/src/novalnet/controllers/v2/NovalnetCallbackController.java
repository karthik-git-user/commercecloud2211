package novalnet.controllers.v2;

import de.hybris.platform.commerceservices.request.mapping.annotation.ApiVersion;
import de.hybris.platform.commercewebservicescommons.dto.order.PaymentDetailsWsDTO;
import de.hybris.platform.webservicescommons.mapping.DataMapper;
import de.hybris.platform.webservicescommons.swagger.ApiFieldsParam;
import de.hybris.platform.webservicescommons.swagger.ApiBaseSiteIdAndUserIdParam;
import de.hybris.platform.store.BaseStoreModel;
import javax.annotation.Resource;

import java.util.HashMap;
import java.util.Map;
import java.math.BigDecimal;

import org.apache.log4j.Logger;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import org.springframework.http.HttpStatus;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;

import static de.hybris.platform.webservicescommons.mapping.FieldSetLevelHelper.DEFAULT_LEVEL;
import de.hybris.platform.webservicescommons.mapping.FieldSetLevelHelper;
import java.net.URL;

import java.nio.charset.StandardCharsets;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.List;
import java.util.Date;
import com.google.gson.Gson;
import java.io.*;
import java.math.*;
import java.util.Calendar;
import java.text.SimpleDateFormat;

import de.hybris.novalnet.core.model.NovalnetPaymentInfoModel;
import de.hybris.novalnet.core.model.NovalnetCallbackInfoModel;
import de.hybris.platform.core.model.order.CartModel;
import de.hybris.platform.core.enums.OrderStatus;
import de.hybris.platform.order.PaymentModeService;
import de.hybris.platform.commercefacades.order.OrderFacade;
import de.hybris.platform.commercefacades.user.data.CountryData;
import de.hybris.platform.commercefacades.order.data.CartData;
import de.novalnet.order.NovalnetOrderFacade;
import novalnet.dto.payment.NnCallbackRequestWsDTO;
import de.novalnet.beans.NnCallbackEventData;
import de.novalnet.beans.NnCallbackMerchantData;
import de.novalnet.beans.NnCallbackResultData;
import de.novalnet.beans.NnCallbackRequestData;
import de.novalnet.beans.NnCallbackResponseData;
import de.novalnet.beans.NnCallbackTransactionData;
import de.novalnet.beans.NnCallbackRefundData;
import de.novalnet.beans.NnCallbackCollectionData;
import novalnet.dto.payment.NnCallbackResponseWsDTO;

import java.net.InetAddress;
import java.net.UnknownHostException;
import javax.servlet.http.HttpServletRequest;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Controller
@RequestMapping(value = "/{baseSiteId}/novalnet")
@ApiVersion("v2")
@Tag(name = "Novalnet Callback")
public class NovalnetCallbackController
{
    private final static Logger LOG = Logger.getLogger(NovalnetCallbackController.class);

    protected static final String DEFAULT_FIELD_SET = FieldSetLevelHelper.DEFAULT_LEVEL;

    private BaseStoreModel baseStore;
    private CartData cartData;
    private CartModel cartModel;
    private String password;
    public String callbackComments;
    public String transactionStatus;
    public String requestPaymentType;
    public String currentDate;

    private static final String PAYMENT_AUTHORIZE = "AUTHORIZE";
    public static final int REQUEST_IP = 4;
    private boolean testMode = false;
    private boolean errorFlag = false;

    public Map<String, String> capturePayments = new HashMap<String, String>();
    public Map<String, String> cancelPayments = new HashMap<String, String>();
    public Map<String, String> updatePayments = new HashMap<String, String>();
    public Map<String, String> refundPayments = new HashMap<String, String>();
    public Map<String, String> creditPayments = new HashMap<String, String>();
    public Map<String, String> initialPayments = new HashMap<String, String>();
    public Map<String, String> chargebackPayments = new HashMap<String, String>();
    public Map<String, String> collectionPayments = new HashMap<String, String>();
    public Map<String, String[]> paymentTypes = new HashMap<String, String[]>();

    public NnCallbackEventData eventData =  new NnCallbackEventData();
    public NnCallbackMerchantData merchantData =  new NnCallbackMerchantData();
    public NnCallbackTransactionData transactionData =  new NnCallbackTransactionData();
    public NnCallbackResultData resultData =  new NnCallbackResultData();
    public NnCallbackRefundData refundData =  new NnCallbackRefundData();
    public NnCallbackCollectionData collectionData =  new NnCallbackCollectionData();

    @Resource(name = "novalnetOrderFacade")
    NovalnetOrderFacade novalnetOrderFacade;

    @Resource(name = "dataMapper")
    private DataMapper dataMapper;

    @Resource
    private PaymentModeService paymentModeService;

    private static final String REQUEST_MAPPING = "paymentType,action,cartId,billingAddress(titleCode,firstName,lastName,line1,line2,town,postalCode,country(isocode),region(isocode)),paymentData(panHash,uniqId,iban),returnUrl,tid";

    @RequestMapping(value = "/callback", method = RequestMethod.POST)
    @ResponseStatus(HttpStatus.CREATED)
    @ResponseBody
    @Operation(operationId = "callback", summary = "handle callback request", description = "keeps the transactions in sync between novalnet and the sap commerce")
    @ApiBaseSiteIdAndUserIdParam
    public NnCallbackResponseWsDTO handleCallback(
            @Parameter(description =
    "Request body parameter that contains callback request", required = true) @RequestBody final NnCallbackRequestWsDTO callbackRequest,
            @ApiFieldsParam @RequestParam(defaultValue = DEFAULT_FIELD_SET) final String fields, final HttpServletRequest request)
            throws UnknownHostException, NoSuchAlgorithmException
    {
		currentDate = getCurrentDate();

        NnCallbackResponseData callbackResponseData = new NnCallbackResponseData();
        NnCallbackRequestData callbackRequestData = dataMapper.map(callbackRequest, NnCallbackRequestData.class, fields);
        String ipCheck = checkIP(request);

        if(errorFlag) {
            callbackResponseData.setMessage(ipCheck);
            return dataMapper.map(callbackResponseData, NnCallbackResponseWsDTO.class, fields);
        } else {
            LOG.info(ipCheck);
        }

        String mandateCheck = checkmandateParams(callbackRequestData);

        if(errorFlag) {
            callbackResponseData.setMessage(mandateCheck);
            return dataMapper.map(callbackResponseData, NnCallbackResponseWsDTO.class, fields);
        } else {
            LOG.info(mandateCheck);
        }

        String Checksum = validateChecksum(callbackRequestData);

        if(errorFlag) {
            callbackResponseData.setMessage(Checksum);
            return dataMapper.map(callbackResponseData, NnCallbackResponseWsDTO.class, fields);
        } else {
            LOG.info(Checksum);
        }

        capturePayments.put("CREDITCARD", "CREDITCARD");
        capturePayments.put("INVOICE", "INVOICE");
        capturePayments.put("GUARANTEED_INVOICE", "GUARANTEED_INVOICE");
        capturePayments.put("DIRECT_DEBIT_SEPA", "DIRECT_DEBIT_SEPA");
        capturePayments.put("GUARANTEED_DIRECT_DEBIT_SEPA", "GUARANTEED_DIRECT_DEBIT_SEPA");
        capturePayments.put("PAYPAL", "PAYPAL");

        cancelPayments.put("CREDITCARD", "CREDITCARD");
        cancelPayments.put("INVOICE", "INVOICE");
        cancelPayments.put("GUARANTEED_INVOICE", "GUARANTEED_INVOICE");
        cancelPayments.put("DIRECT_DEBIT_SEPA", "DIRECT_DEBIT_SEPA");
        cancelPayments.put("GUARANTEED_DIRECT_DEBIT_SEPA", "GUARANTEED_DIRECT_DEBIT_SEPA");
        cancelPayments.put("PAYPAL", "PAYPAL");
        cancelPayments.put("PRZELEWY24", "PRZELEWY24");

        updatePayments.put("CREDITCARD", "CREDITCARD");
        updatePayments.put("INVOICE_START", "INVOICE_START");
        updatePayments.put("PREPAYMENT", "PREPAYMENT");
        updatePayments.put("GUARANTEED_INVOICE", "GUARANTEED_INVOICE");
        updatePayments.put("DIRECT_DEBIT_SEPA", "DIRECT_DEBIT_SEPA");
        updatePayments.put("GUARANTEED_DIRECT_DEBIT_SEPA", "GUARANTEED_DIRECT_DEBIT_SEPA");
        updatePayments.put("PAYPAL", "PAYPAL");
        updatePayments.put("PRZELEWY24", "PRZELEWY24");
        updatePayments.put("CASHPAYMENT", "CASHPAYMENT");
        updatePayments.put("POSTFINANCE", "POSTFINANCE");
        updatePayments.put("POSTFINANCE_CARD", "POSTFINANCE_CARD");

        refundPayments.put("CREDITCARD_BOOKBACK", "CREDITCARD_BOOKBACK");
        refundPayments.put("REFUND_BY_BANK_TRANSFER_EU", "REFUND_BY_BANK_TRANSFER_EU");
        refundPayments.put("PAYPAL_BOOKBACK", "PAYPAL_BOOKBACK");
        refundPayments.put("PRZELEWY24_REFUND", "PRZELEWY24_REFUND");
        refundPayments.put("CASHPAYMENT_REFUND", "CASHPAYMENT_REFUND");
        refundPayments.put("POSTFINANCE_REFUND", "POSTFINANCE_REFUND");
        refundPayments.put("GUARANTEED_INVOICE_BOOKBACK", "GUARANTEED_INVOICE_BOOKBACK");
        refundPayments.put("GUARANTEED_SEPA_BOOKBACK", "GUARANTEED_SEPA_BOOKBACK");
        refundPayments.put("APPLEPAY_BOOKBACK", "APPLEPAY_BOOKBACK");
        refundPayments.put("GOOGLEPAY_BOOKBACK", "GOOGLEPAY_BOOKBACK");
        refundPayments.put("WECHATPAY_REFUND", "WECHATPAY_REFUND");
        refundPayments.put("ALIPAY_REFUND", "ALIPAY_REFUND");
        refundPayments.put("TRUSTLY_REFUND", "TRUSTLY_REFUND");

        creditPayments.put("INVOICE_CREDIT", "INVOICE_CREDIT");
        creditPayments.put("CREDIT_ENTRY_CREDITCARD", "CREDIT_ENTRY_CREDITCARD");
        creditPayments.put("CREDIT_ENTRY_SEPA", "CREDIT_ENTRY_SEPA");
        creditPayments.put("DEBT_COLLECTION_SEPA", "DEBT_COLLECTION_SEPA");
        creditPayments.put("DEBT_COLLECTION_CREDITCARD", "DEBT_COLLECTION_CREDITCARD");
        creditPayments.put("GUARANTEED_DEBT_COLLECTION", "GUARANTEED_DEBT_COLLECTION");
        creditPayments.put("CASHPAYMENT_CREDIT", "CASHPAYMENT_CREDIT");
        creditPayments.put("ONLINE_TRANSFER_CREDIT", "ONLINE_TRANSFER_CREDIT");
        creditPayments.put("MULTIBANCO_CREDIT", "MULTIBANCO_CREDIT");
        creditPayments.put("CREDIT_ENTRY_DE", "CREDIT_ENTRY_DE");
        creditPayments.put("CREDITCARD_REPRESENTMENT", "CREDITCARD_REPRESENTMENT");
        creditPayments.put("BANK_TRANSFER_BY_END_CUSTOMER", "BANK_TRANSFER_BY_END_CUSTOMER");
        creditPayments.put("GOOGLEPAY_REPRESENTMENT", "GOOGLEPAY_REPRESENTMENT");
        creditPayments.put("APPLEPAY_REPRESENTMENT", "APPLEPAY_REPRESENTMENT");

        initialPayments.put("CREDITCARD", "CREDITCARD");
        initialPayments.put("INVOICE_START", "INVOICE_START");
        initialPayments.put("GUARANTEED_INVOICE", "GUARANTEED_INVOICE");
        initialPayments.put("DIRECT_DEBIT_SEPA", "DIRECT_DEBIT_SEPA");
        initialPayments.put("GUARANTEED_DIRECT_DEBIT_SEPA", "GUARANTEED_DIRECT_DEBIT_SEPA");
        initialPayments.put("GUARANTEED_INSTALLMENT_PAYMENT", "GUARANTEED_INSTALLMENT_PAYMENT");
        initialPayments.put("PAYPAL", "PAYPAL");
        initialPayments.put("ONLINE_TRANSFER", "ONLINE_TRANSFER");
        initialPayments.put("ONLINE_BANK_TRANSFER", "ONLINE_BANK_TRANSFER");
        initialPayments.put("IDEAL", "IDEAL");
        initialPayments.put("EPS", "EPS");
        initialPayments.put("PAYSAFECARD", "PAYSAFECARD");
        initialPayments.put("GIROPAY", "GIROPAY");
        initialPayments.put("PRZELEWY24", "PRZELEWY24");
        initialPayments.put("CASHPAYMENT", "CASHPAYMENT");
        initialPayments.put("POSTFINANCE", "POSTFINANCE");
        initialPayments.put("POSTFINANCE_CARD", "POSTFINANCE_CARD");

        chargebackPayments.put("RETURN_DEBIT_SEPA", "RETURN_DEBIT_SEPA");
        chargebackPayments.put("REVERSAL", "REVERSAL");
        chargebackPayments.put("CREDITCARD_CHARGEBACK", "CREDITCARD_CHARGEBACK");
        chargebackPayments.put("PAYPAL_CHARGEBACK", "PAYPAL_CHARGEBACK");
        chargebackPayments.put("APPLEPAY_CHARGEBACK", "APPLEPAY_CHARGEBACK");
        chargebackPayments.put("GOOGLEPAY_CHARGEBACK", "GOOGLEPAY_CHARGEBACK");

        collectionPayments.put("INVOICE_CREDIT", "INVOICE_CREDIT");
        collectionPayments.put("CREDIT_ENTRY_CREDITCARD", "CREDIT_ENTRY_CREDITCARD");
        collectionPayments.put("CREDIT_ENTRY_SEPA", "CREDIT_ENTRY_SEPA");
        collectionPayments.put("GUARANTEED_CREDIT_ENTRY_SEPA", "GUARANTEED_CREDIT_ENTRY_SEPA");
        collectionPayments.put("DEBT_COLLECTION_SEPA", "DEBT_COLLECTION_SEPA");
        collectionPayments.put("DEBT_COLLECTION_CREDITCARD", "DEBT_COLLECTION_CREDITCARD");
        collectionPayments.put("GUARANTEED_DEBT_COLLECTION", "GUARANTEED_DEBT_COLLECTION");
        collectionPayments.put("CASHPAYMENT_CREDIT", "CASHPAYMENT_CREDIT");
        collectionPayments.put("DEBT_COLLECTION_DE", "DEBT_COLLECTION_DE");

        // Payment types for each payment method
        String[] creditCardPaymentTypes = {"CREDITCARD", "CREDITCARD_CHARGEBACK", "CREDITCARD_BOOKBACK", "TRANSACTION_CANCELLATION", "CREDIT_ENTRY_CREDITCARD", "DEBT_COLLECTION_CREDITCARD", "CREDITCARD_REPRESENTMENT", "PAYMENT_REMINDER_1", "PAYMENT_REMINDER_2", "SUBMISSION_TO_COLLECTION_AGENCY"};
        String[] directDebitSepaPaymentTypes = {"DIRECT_DEBIT_SEPA", "RETURN_DEBIT_SEPA", "REFUND_BY_BANK_TRANSFER_EU", "TRANSACTION_CANCELLATION", "CREDIT_ENTRY_SEPA", "DEBT_COLLECTION_SEPA", "BANK_TRANSFER_BY_END_CUSTOMER", "PAYMENT_REMINDER_1", "PAYMENT_REMINDER_2", "SUBMISSION_TO_COLLECTION_AGENCY"};
        String[] invoicePaymentTypes = {"INVOICE_START", "INVOICE_CREDIT", "TRANSACTION_CANCELLATION", "REFUND_BY_BANK_TRANSFER_EU", "CREDIT_ENTRY_DE", "DEBT_COLLECTION_DE", "INVOICE", "PAYMENT_REMINDER_1", "PAYMENT_REMINDER_2", "SUBMISSION_TO_COLLECTION_AGENCY", "BANK_TRANSFER_BY_END_CUSTOMER"};
        String[] prepaymentPaymentTypes = {"PREPAYMENT", "INVOICE_CREDIT", "REFUND_BY_BANK_TRANSFER_EU", "CREDIT_ENTRY_DE", "DEBT_COLLECTION_DE", "SUBMISSION_TO_COLLECTION_AGENCY", "BANK_TRANSFER_BY_END_CUSTOMER"};
        String[] multibancoPaymentTypes = {"MULTIBANCO", "MULTIBANCO_CREDIT", "PAYMENT_REMINDER_1", "PAYMENT_REMINDER_2", "SUBMISSION_TO_COLLECTION_AGENCY"};
        String[] payPalPaymentTypes = {"PAYPAL", "PAYPAL_BOOKBACK", "PAYPAL_CHARGEBACK", "REFUND_BY_BANK_TRANSFER_EU", "PAYMENT_REMINDER_1", "PAYMENT_REMINDER_2", "CREDIT_ENTRY_DE", "SUBMISSION_TO_COLLECTION_AGENCY"};
        String[] instantBankTransferPaymentTypes = {"ONLINE_TRANSFER", "REFUND_BY_BANK_TRANSFER_EU", "CREDIT_ENTRY_DE", "REVERSAL", "DEBT_COLLECTION_DE", "ONLINE_TRANSFER_CREDIT", "PAYMENT_REMINDER_1", "PAYMENT_REMINDER_2", "SUBMISSION_TO_COLLECTION_AGENCY"};
        String[] onlineBankTransferPaymentTypes = {"ONLINE_BANK_TRANSFER", "REFUND_BY_BANK_TRANSFER_EU", "CREDIT_ENTRY_DE", "REVERSAL", "DEBT_COLLECTION_DE", "ONLINE_TRANSFER_CREDIT", "PAYMENT_REMINDER_1", "PAYMENT_REMINDER_2", "SUBMISSION_TO_COLLECTION_AGENCY"};
        String[] bancontactPaymentTypes = {"BANCONTACT", "REFUND_BY_BANK_TRANSFER_EU", "PAYMENT_REMINDER_1", "PAYMENT_REMINDER_2", "SUBMISSION_TO_COLLECTION_AGENCY"};
        String[] idealPaymentTypes = {"IDEAL", "REFUND_BY_BANK_TRANSFER_EU", "CREDIT_ENTRY_DE", "REVERSAL", "DEBT_COLLECTION_DE", "ONLINE_TRANSFER_CREDIT", "PAYMENT_REMINDER_1", "PAYMENT_REMINDER_2", "SUBMISSION_TO_COLLECTION_AGENCY"};
        String[] epsPaymentTypes = {"EPS", "REFUND_BY_BANK_TRANSFER_EU", "CREDIT_ENTRY_DE", "REVERSAL", "DEBT_COLLECTION_DE", "ONLINE_TRANSFER_CREDIT", "PAYMENT_REMINDER_1", "PAYMENT_REMINDER_2"};
        String[] giropayPaymentTypes = {"GIROPAY", "REFUND_BY_BANK_TRANSFER_EU", "CREDIT_ENTRY_DE", "REVERSAL", "DEBT_COLLECTION_DE", "ONLINE_TRANSFER_CREDIT", "PAYMENT_REMINDER_1", "PAYMENT_REMINDER_2"};
        String[] przelewy24PaymentTypes = {"PRZELEWY24", "PRZELEWY24_REFUND", "PAYMENT_REMINDER_1", "PAYMENT_REMINDER_2"};
        String[] cashpaymentPaymentTypes = {"CASHPAYMENT", "CASHPAYMENT_REFUND", "CASHPAYMENT_CREDIT", "PAYMENT_REMINDER_1", "PAYMENT_REMINDER_2", "SUBMISSION_TO_COLLECTION_AGENCY"};
        String[] postFinancePaymentTypes = {"POSTFINANCE", "POSTFINANCE_REFUND", "PAYMENT_REMINDER_1", "PAYMENT_REMINDER_2", "SUBMISSION_TO_COLLECTION_AGENCY"};
        String[] postFinanceCardPaymentTypes = {"POSTFINANCE_CARD", "POSTFINANCE_REFUND", "PAYMENT_REMINDER_1", "PAYMENT_REMINDER_2", "SUBMISSION_TO_COLLECTION_AGENCY"};
        String[] guaranteedInvoicePaymentTypes = {"GUARANTEED_INVOICE", "GUARANTEED_INVOICE_BOOKBACK", "PAYMENT_REMINDER_1", "PAYMENT_REMINDER_2", "SUBMISSION_TO_COLLECTION_AGENCY"};
        String[] guaranteedDirectDebitSepaPaymentTypes = {"GUARANTEED_DIRECT_DEBIT_SEPA", "GUARANTEED_SEPA_BOOKBACK", "PAYMENT_REMINDER_1", "PAYMENT_REMINDER_2", "SUBMISSION_TO_COLLECTION_AGENCY"};

        paymentTypes.put("novalnetCreditCard", creditCardPaymentTypes);
        paymentTypes.put("novalnetDirectDebitSepa", directDebitSepaPaymentTypes);
        paymentTypes.put("novalnetInvoice", invoicePaymentTypes);
        paymentTypes.put("novalnetPrepayment", prepaymentPaymentTypes);
        paymentTypes.put("novalnetPayPal", payPalPaymentTypes);
        paymentTypes.put("novalnetInstantBankTransfer", instantBankTransferPaymentTypes);
        paymentTypes.put("novalnetOnlineBankTransfer", onlineBankTransferPaymentTypes);
        paymentTypes.put("novalnetIdeal", idealPaymentTypes);
        paymentTypes.put("novalnetEps", epsPaymentTypes);
        paymentTypes.put("novalnetGiropay", giropayPaymentTypes);
        paymentTypes.put("novalnetPrzelewy24", przelewy24PaymentTypes);
        paymentTypes.put("novalnetBarzahlen", cashpaymentPaymentTypes);
        paymentTypes.put("novalnetPostFinance", postFinancePaymentTypes);
        paymentTypes.put("novalnetPostFinanceCard", postFinanceCardPaymentTypes);
        paymentTypes.put("novalnetGuaranteedDirectDebitSepa", guaranteedDirectDebitSepaPaymentTypes);
        paymentTypes.put("novalnetGuaranteedInvoice", guaranteedInvoicePaymentTypes);
        paymentTypes.put("novalnetMultibanco", multibancoPaymentTypes);
        paymentTypes.put("novalnetBancontact", bancontactPaymentTypes);

        eventData       =  callbackRequestData.getEvent();
        merchantData    =  callbackRequestData.getMerchant();
        transactionData =  callbackRequestData.getTransaction();
        resultData      =  callbackRequestData.getResult();

        String response = "";
        String[] refundType = {"CHARGEBACK", "TRANSACTION_REFUND"};
        String referenceTid = transactionData.getTid();
        requestPaymentType = transactionData.getPayment_type();

        if("TRANSACTION_REFUND".equals(eventData.getType())) {
            refundData =  transactionData.getRefund();
            requestPaymentType = refundData.getPayment_type();
        }


        String[] pendingPaymentType = {"PAYPAL", "PRZELEWY24", "POSTFINANCE_CARD", "POSTFINANCE"};
        String[] reminderPaymentType = {"PAYMENT_REMINDER_1", "PAYMENT_REMINDER_2"};

        if ((chargebackPayments.containsValue(requestPaymentType) || collectionPayments.containsValue(requestPaymentType)) || creditPayments.containsValue(requestPaymentType) || refundPayments.containsValue(requestPaymentType)) {

            referenceTid = eventData.getParent_tid();

            if(referenceTid.length() != 17) {
                callbackResponseData.setMessage("Parent TID is not Valid");
                return dataMapper.map(callbackResponseData, NnCallbackResponseWsDTO.class, fields);
            }
        }

        final List<NovalnetCallbackInfoModel> orderReference = novalnetOrderFacade.getCallbackInfo(referenceTid);
        String paymentType = orderReference.get(0).getPaymentType();
        String[] suppotedCallbackPaymentTypes = paymentTypes.get(paymentType);

        transactionStatus = transactionData.getStatus();

        LOG.info("Webhook request recieved event type" + eventData.getType() + " Payment type recieved " + requestPaymentType);

        if (Arrays.asList(refundType).contains(eventData.getType())) {
            response = performRefund(callbackRequestData);
        } else if ("CREDIT".equals(eventData.getType())) {
            response = performCredit(callbackRequestData);
        } else if ("TRANSACTION_CANCEL".equals(eventData.getType())) {
            response = performTransactionCancel(callbackRequestData);
        } else if ("TRANSACTION_CAPTURE".equals(eventData.getType())) {
            response = performTransactionCapture(callbackRequestData);
        } else if ("PAYMENT".equals(eventData.getType())) {
            response = performStatusUpdate(callbackRequestData);
        } else if ("TRANSACTION_UPDATE".equals(eventData.getType())) {
            response = performTransacrionUpdate(callbackRequestData);
        } else if (Arrays.asList(reminderPaymentType).contains(eventData.getType())) {
            response = performReminderUpdate(callbackRequestData);
        } else if ("SUBMISSION_TO_COLLECTION_AGENCY".equals(eventData.getType())) {
            response = performCollectionUpdate(callbackRequestData);
        }

        callbackResponseData.setMessage(response);
        return dataMapper.map(callbackResponseData, NnCallbackResponseWsDTO.class, fields);

    }

    public String performTransacrionUpdate(NnCallbackRequestData callbackRequestData) {

        String[] pendingPaymentType = {"PAYPAL", "PRZELEWY24", "POSTFINANCE_CARD", "POSTFINANCE"};
        String[] processPaymentType = {"DIRECT_DEBIT_SEPA", "INVOICE_START", "GUARANTEED_DIRECT_DEBIT_SEPA", "GUARANTEED_INVOICE", "CREDITCARD", "PAYPAL", "GOOGLEPAY", "APPLEPAY"};

        final List<NovalnetCallbackInfoModel> orderReference = novalnetOrderFacade.getCallbackInfo(transactionData.getTid().toString());

        String orderNo = orderReference.get(0).getOrderNo();

        int paidAmount = orderReference.get(0).getPaidAmount();

        int orderAmount = orderReference.get(0).getOrderAmount();

        int amountInCents = Integer.parseInt(transactionData.getAmount().toString());

        int totalAmount = paidAmount + amountInCents;

        long callbackTid = Long.parseLong(transactionData.getTid().toString());

        long amountToBeFormat = Integer.parseInt(transactionData.getAmount().toString());
        BigDecimal formattedAmount = new BigDecimal(amountToBeFormat).movePointLeft(2);

        final List<NovalnetPaymentInfoModel> paymentInfo = novalnetOrderFacade.getNovalnetPaymentInfo(orderReference.get(0).getOrderNo());
        NovalnetPaymentInfoModel paymentInfoModel = novalnetOrderFacade.getPaymentModel(paymentInfo);

        if (Arrays.asList(processPaymentType).contains(transactionData.getPayment_type()) && "PENDING".equals(paymentInfo.get(0).getPaymentGatewayStatus())) {

            if ("ON_HOLD".equals(transactionData.getStatus().toString())) {

                callbackComments = "The transaction status has been changed from pending to on-hold for the TID:  " + transactionData.getTid().toString() + " on " + currentDate.toString();
                novalnetOrderFacade.updateCallbackComments(callbackComments, orderNo, transactionStatus);
                novalnetOrderFacade.updatePaymentInfo(paymentInfo, transactionData.getStatus().toString());
                paymentInfoModel = novalnetOrderFacade.getPaymentModel(paymentInfo);
                novalnetOrderFacade.updateOrderStatus(orderNo, paymentInfoModel);
                return callbackComments;
            } else if ("CONFIRMED".equals(transactionData.getStatus().toString())) {
                callbackComments = (("75".equals(paymentInfo.get(0).getPaymentGatewayStatus())) && "GUARANTEED_INVOICE".equals(transactionData.getPayment_type())) ? "The transaction has been confirmed successfully for the TID: " + transactionData.getTid().toString() + "and the due date updated as" + transactionData.getDue_date().toString() + "This is processed as a guarantee payment" : "The transaction has been confirmed on " + currentDate.toString();

                novalnetOrderFacade.updatePaymentInfo(paymentInfo, transactionData.getStatus().toString());
                paymentInfoModel = novalnetOrderFacade.getPaymentModel(paymentInfo);
                novalnetOrderFacade.updateOrderStatus(orderNo, paymentInfoModel);
                novalnetOrderFacade.updateCallbackComments(callbackComments, orderNo, transactionStatus);
                return callbackComments;
            } else {
                return "no actions done for transaction update";
            }
        }

        if (Arrays.asList(pendingPaymentType).contains(transactionData.getPayment_type())) {

            if (orderAmount > paidAmount) {
                String[] statusPending = {"PENDING"};
                if (Arrays.asList(statusPending).contains(paymentInfo.get(0).getPaymentGatewayStatus()) && "CONFIRMED".equals(transactionData.getStatus().toString())) {
                        callbackComments = "The transaction has been confirmed on " + currentDate.toString();
                        novalnetOrderFacade.updatePaymentInfo(paymentInfo, transactionData.getStatus().toString());
                        paymentInfoModel = novalnetOrderFacade.getPaymentModel(paymentInfo);
                        novalnetOrderFacade.updateOrderStatus(orderNo, paymentInfoModel);
                } else {
                    String reasonText = !("".equals(resultData.getStatus_desc().toString())) ? resultData.getStatus_desc().toString() : (!("".equals(resultData.getStatus().toString())) ? resultData.getStatus_text().toString() : "Payment could not be completed");
                    callbackComments = "The transaction has been cancelled due to:" + reasonText;
                    novalnetOrderFacade.updatePaymentInfo(paymentInfo, transactionData.getStatus().toString());
                    novalnetOrderFacade.updateCancelStatus(orderNo);
                }
                // Update callback comments
                novalnetOrderFacade.updateCallbackComments(callbackComments, orderNo, transactionStatus);
                // Update Callback info
                novalnetOrderFacade.updateCallbackInfo(callbackTid, orderReference, totalAmount);
                return callbackComments;
            }
            return "Novalnet webhook script executed. Not applicable for this process";
        }

        return "Novalnet webhook script executed. No action executed for transaction update";

    }

    public String performReminderUpdate(NnCallbackRequestData callbackRequestData) {

        final List<NovalnetCallbackInfoModel> orderReference = novalnetOrderFacade.getCallbackInfo(transactionData.getTid().toString());
        String orderNo = orderReference.get(0).getOrderNo();

        callbackComments = ("PAYMENT_REMINDER_1".equals(eventData.getType())) ? "Payment Reminder 1 has been sent to the customer" : "Payment Reminder 2 has been sent to the customer" ;

        novalnetOrderFacade.updateCallbackComments(callbackComments, orderNo, transactionStatus);
        return callbackComments;
    }

    public String performCollectionUpdate(NnCallbackRequestData callbackRequestData) {

        collectionData =  callbackRequestData.getCollection();

        final List<NovalnetCallbackInfoModel> orderReference = novalnetOrderFacade.getCallbackInfo(transactionData.getTid().toString());
        String orderNo = orderReference.get(0).getOrderNo();

        callbackComments = "The transaction has been submitted to the collection agency. Collection Reference: " + collectionData.getReference();

        novalnetOrderFacade.updateCallbackComments(callbackComments, orderNo, transactionStatus);
        return callbackComments;
    }

    public String performStatusUpdate(NnCallbackRequestData callbackRequestData) {

        final List<NovalnetCallbackInfoModel> orderReference = novalnetOrderFacade.getCallbackInfo(transactionData.getTid().toString());
        String orderNo = orderReference.get(0).getOrderNo();

        final List<NovalnetPaymentInfoModel> paymentInfo = novalnetOrderFacade.getNovalnetPaymentInfo(orderReference.get(0).getOrderNo());
        NovalnetPaymentInfoModel paymentInfoModel = novalnetOrderFacade.getPaymentModel(paymentInfo);

        paymentInfoModel = novalnetOrderFacade.getPaymentModel(paymentInfo);
        novalnetOrderFacade.updateOrderStatus(orderNo, paymentInfoModel);
        novalnetOrderFacade.updatePaymentInfo(paymentInfo, transactionData.getStatus().toString());
        return "Novalnet webhook script executed. Status updated for initial transaction";
    }

    public String performTransactionCapture(NnCallbackRequestData callbackRequestData) {

        final List<NovalnetCallbackInfoModel> orderReference = novalnetOrderFacade.getCallbackInfo(transactionData.getTid().toString());
        String orderNo = orderReference.get(0).getOrderNo();

        final List<NovalnetPaymentInfoModel> paymentInfo = novalnetOrderFacade.getNovalnetPaymentInfo(orderReference.get(0).getOrderNo());
        NovalnetPaymentInfoModel paymentInfoModel = novalnetOrderFacade.getPaymentModel(paymentInfo);
        callbackComments = ("GUARANTEED_INVOICE".equals(transactionData.getPayment_type()) || "INVOICE".equals(transactionData.getPayment_type())) ? "The transaction has been confirmed successfully for the TID:" + transactionData.getTid().toString() + " and the due date updated as " + transactionData.getDue_date().toString() : "The transaction has been confirmed on " + currentDate.toString();

        novalnetOrderFacade.updatePaymentInfo(paymentInfo, transactionData.getStatus().toString());
        paymentInfoModel = novalnetOrderFacade.getPaymentModel(paymentInfo);
        novalnetOrderFacade.updateOrderStatus(orderNo, paymentInfoModel);
        novalnetOrderFacade.updateCallbackComments(callbackComments, orderNo, transactionStatus);
        return callbackComments;
    }


    public String performTransactionCancel(NnCallbackRequestData callbackRequestData) {

        final List<NovalnetCallbackInfoModel> orderReference = novalnetOrderFacade.getCallbackInfo(transactionData.getTid().toString());
        String orderNo = orderReference.get(0).getOrderNo();

        final List<NovalnetPaymentInfoModel> paymentInfo = novalnetOrderFacade.getNovalnetPaymentInfo(orderReference.get(0).getOrderNo());

        callbackComments = "The transaction has been canceled on " + currentDate.toString();
        novalnetOrderFacade.updatePaymentInfo(paymentInfo, transactionData.getStatus().toString());
        novalnetOrderFacade.updateCancelStatus(orderNo);
        novalnetOrderFacade.updateCallbackComments(callbackComments, orderNo, transactionStatus);
        return callbackComments;
    }

    public String performCredit(NnCallbackRequestData callbackRequestData) {

        final List<NovalnetCallbackInfoModel> orderReference = novalnetOrderFacade.getCallbackInfo(eventData.getParent_tid());
        String orderNo = orderReference.get(0).getOrderNo();

        int amountInCents = Integer.parseInt(transactionData.getAmount().toString());

        int paidAmount = orderReference.get(0).getPaidAmount();

        int orderAmount = orderReference.get(0).getOrderAmount();

        int totalAmount = paidAmount + amountInCents;

        long amountToBeFormat = Integer.parseInt(transactionData.getAmount().toString());
        BigDecimal formattedAmount = new BigDecimal(amountToBeFormat).movePointLeft(2);

        String paymentType = orderReference.get(0).getPaymentType();

        String notifyComments = "";

        long callbackTid = Long.parseLong(transactionData.getTid().toString());


        String[] creditPaymentType = {"INVOICE_CREDIT", "CASHPAYMENT_CREDIT", "MULTIBANCO_CREDIT"};

        LOG.info("payment type credit : " + transactionData.getPayment_type());

        if (Arrays.asList(creditPaymentType).contains(transactionData.getPayment_type())) {
            // if settlement of invoice OR Advance payment through Customer
            if (orderAmount > paidAmount) {
                // Form callback comments
                notifyComments = callbackComments = "Credit has been successfully received for the TID: " + eventData.getParent_tid().toString() + " with amount: " + formattedAmount + " " + transactionData.getCurrency().toString() + " on " + currentDate.toString() + ". Please refer PAID order details in our Novalnet Admin Portal for the TID: " + transactionData.getTid().toString();

                // Update PART PAID payment status
                novalnetOrderFacade.updatePartPaidStatus(orderNo);

                // Update Callback info
                novalnetOrderFacade.updateCallbackInfo(callbackTid, orderReference, totalAmount);

                // Full amount paid by the customer
                if (totalAmount >= orderAmount) {
                    // Update Callback order status
                    final List<NovalnetPaymentInfoModel> paymentInfo = novalnetOrderFacade.getNovalnetPaymentInfo(orderReference.get(0).getOrderNo());
                    NovalnetPaymentInfoModel paymentInfoModel = novalnetOrderFacade.getPaymentModel(paymentInfo);

                    novalnetOrderFacade.updatePaymentInfo(paymentInfo, transactionData.getStatus().toString());
                    paymentInfoModel = novalnetOrderFacade.getPaymentModel(paymentInfo);
                    novalnetOrderFacade.updateOrderStatus(orderNo, paymentInfoModel);

                    // Customer paid greater than the order amount
                    if (totalAmount > orderAmount) {
                        notifyComments += ". Customer paid amount is greater than order amount.";
                    }
                }

                // Update callback comments
                novalnetOrderFacade.updateCallbackComments(callbackComments, orderNo, transactionStatus);
                return callbackComments;
            }
        } else {
            callbackComments = "Credit has been successfully received for the TID: " + eventData.getParent_tid().toString() + " with amount: " + formattedAmount + " " + transactionData.getCurrency().toString() + " on " + currentDate.toString() + ". Please refer PAID order details in our Novalnet Admin Portal for the TID:" + transactionData.getTid().toString() + ".";
            novalnetOrderFacade.updateCallbackInfo(callbackTid, orderReference, totalAmount);
            novalnetOrderFacade.updateCallbackComments(callbackComments, orderNo, transactionStatus);
            return callbackComments;
        }

        return "no action executed for credit";
    }

    public String performRefund(NnCallbackRequestData callbackRequestData) {

        final List<NovalnetCallbackInfoModel> orderReference = novalnetOrderFacade.getCallbackInfo(eventData.getParent_tid());
        String orderNo = orderReference.get(0).getOrderNo();
        Integer doStausUpdate = 0;

        final List<NovalnetPaymentInfoModel> paymentInfo = novalnetOrderFacade.getNovalnetPaymentInfo(orderNo);
        NovalnetPaymentInfoModel paymentInfoModel = novalnetOrderFacade.getPaymentModel(paymentInfo);

        if(refundPayments.containsValue(requestPaymentType) ||  chargebackPayments.containsValue(requestPaymentType)) {

            String[] chargeBackPaymentType = {"CREDITCARD_CHARGEBACK", "PAYPAL_CHARGEBACK", "RETURN_DEBIT_SEPA", "REVERSAL", "APPLEPAY_CHARGEBACK", "GOOGLEPAY_CHARGEBACK"};
            BigDecimal refundFormattedAmount = new BigDecimal(0);

            int amountInCents = Integer.parseInt(transactionData.getAmount().toString());

            if(!Arrays.asList(chargeBackPaymentType).contains(requestPaymentType)) {
                long refundAmountToBeFormat = Integer.parseInt(refundData.getAmount());
                doStausUpdate = 1;
                amountInCents = Integer.parseInt(transactionData.getRefunded_amount().toString());
                // Format the order amount to currency format
                refundFormattedAmount = new BigDecimal(refundAmountToBeFormat).movePointLeft(2);
            }

            long amountToBeFormat = Integer.parseInt(transactionData.getAmount().toString());
            BigDecimal formattedAmount = new BigDecimal(amountToBeFormat).movePointLeft(2);

            String stidMsg = ". The subsequent TID: ";

            if(Arrays.asList(chargeBackPaymentType).contains(requestPaymentType)) {
                callbackComments = "Chargeback executed successfully for the TID: " + eventData.getParent_tid().toString() + " amount: " + formattedAmount + " " + transactionData.getCurrency() + " on " + currentDate.toString() + stidMsg + transactionData.getTid().toString();
            } else if("REVERSAL".equals(requestPaymentType)) {
                callbackComments = "Chargeback executed for reversal of TID:" + eventData.getParent_tid().toString() + " with the amount  " + formattedAmount + " " + transactionData.getCurrency().toString() + " on " + currentDate.toString() + stidMsg + transactionData.getTid().toString();
            } else if("RETURN_DEBIT_SEPA".equals(requestPaymentType)) {
                callbackComments = "Chargeback executed for return debit of TID:" + eventData.getParent_tid().toString() + " with the amount  " + formattedAmount + " " + transactionData.getCurrency().toString() + " on " + currentDate.toString() + stidMsg + transactionData.getTid().toString();
            } else {
                callbackComments =  "Refund has been initiated for the TID " + eventData.getParent_tid().toString() + " with the amount : " + refundFormattedAmount + " " + transactionData.getCurrency().toString() + ". New TID: " + refundData.getTid().toString() + " for the refunded amount";
            }

            int orderAmount = orderReference.get(0).getOrderAmount();

            if (amountInCents >= orderAmount && doStausUpdate == 1) {
                novalnetOrderFacade.updatePaymentInfo(paymentInfo, transactionData.getStatus().toString());
                novalnetOrderFacade.updateCancelStatus(orderNo);
            }

            // Update callback comments
            novalnetOrderFacade.updateCallbackComments(callbackComments, orderNo, transactionStatus);
            return callbackComments;
        } else {
            return "Payment type " + requestPaymentType + " is not supported for event type " + eventData.getType();
        }

    }

    public String checkmandateParams(NnCallbackRequestData callbackRequestData) {

        errorFlag = false;

        NnCallbackEventData eventData =  callbackRequestData.getEvent();
        NnCallbackMerchantData merchantData =  callbackRequestData.getMerchant();
        NnCallbackTransactionData transactionData =  callbackRequestData.getTransaction();
        NnCallbackResultData resultData =  callbackRequestData.getResult();

        try {

            if(eventData.getType() != null && eventData.getChecksum() != null && eventData.getTid() != null && merchantData.getVendor() != null && merchantData.getProject() != null && transactionData.getTid() != null && transactionData.getPayment_type() != null && transactionData.getStatus() != null && resultData.getStatus() != null ) {
                if (!("".equals(transactionData.getTid())) && transactionData.getTid().toString().length() != 17) {
                    errorFlag = true;
                    return "TID is not valid";
                }
                return "Mandatory params are recieved";
            } else {
                errorFlag = true;
                return "Mandatory params are empty in callback request";
            }
        } catch(RuntimeException ex) {
            errorFlag = true;
            return "Mandatory params validation failed " + ex;
        }

    }

    public String checkIP(HttpServletRequest request) {

        errorFlag = false;

        String vendorScriptHostIpAddress = "";
        final BaseStoreModel baseStore = novalnetOrderFacade.getBaseStoreModel();

        try {
            InetAddress address = InetAddress.getByName("pay-nn.de"); //Novalnet vendor script host
            vendorScriptHostIpAddress = address.getHostAddress();
        } catch (UnknownHostException e) {
            errorFlag = true;
            return "error while fetching novalnet IP address : " + e;
        }

        String callerIp = request.getHeader("HTTP_X_FORWARDED_FOR");

        if (callerIp == null || callerIp.split("[.]").length != REQUEST_IP) {
            callerIp = request.getRemoteAddr();
        }


        testMode = baseStore.getNovalnetVendorscriptTestMode();
        LOG.info("novalnet vecdor script test mode : " + testMode);

        if (!vendorScriptHostIpAddress.equals(callerIp) && !testMode) {
            errorFlag = true;
            return "Novalnet webhook received. Unauthorised access from the IP " + callerIp;
        }


        return "IP validation passed for Callback request";
    }

    public String validateChecksum(NnCallbackRequestData callbackRequestData) throws NoSuchAlgorithmException {

        errorFlag = false;

        final BaseStoreModel baseStore = novalnetOrderFacade.getBaseStoreModel();

        NnCallbackEventData eventData =  callbackRequestData.getEvent();
        NnCallbackMerchantData merchantData =  callbackRequestData.getMerchant();
        NnCallbackTransactionData transactionData =  callbackRequestData.getTransaction();
        NnCallbackResultData resultData =  callbackRequestData.getResult();

        String tokenString = eventData.getTid() + eventData.getType() + resultData.getStatus();

        if (transactionData.getAmount() != null) {
            tokenString +=  transactionData.getAmount();
        }

        if (transactionData.getCurrency() != null) {
            tokenString +=   transactionData.getCurrency();
        }

        if (!"".equals(baseStore.getNovalnetPaymentAccessKey())) {
            tokenString += new StringBuilder(baseStore.getNovalnetPaymentAccessKey().trim()).reverse().toString();
        } else {
            errorFlag = true;
            return "Payment Access key is not configured in backend";
        }

        String createdHash = "";

        try{
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(tokenString.getBytes(StandardCharsets.UTF_8));
            StringBuffer hexString = new StringBuffer();

            for (int i = 0; i < hash.length; i++) {
                String hex = Integer.toHexString(0xff & hash[i]);
                if(hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }

            createdHash =  hexString.toString();
        } catch(RuntimeException ex) {
            errorFlag = true;
            return "RuntimeException while generating checksum " + ex;
        }

        LOG.info("Created hash "+ createdHash);

        if ( !eventData.getChecksum().equals(createdHash) ) {
            errorFlag = true;
            return "While notifying some data has been changed. The hash check failed";
        } else {
            return "Chacksum validated for the callback request";
        }
    }
    
    public static String getCurrentDate() {
        Calendar cal = Calendar.getInstance();
        cal.setTime(new Date());
        
        SimpleDateFormat formatter = new SimpleDateFormat("dd-MM-yyyy, HH:mm:ss");
        String formattedDueDate = formatter.format(cal.getTime());
        return formattedDueDate;
    }


}

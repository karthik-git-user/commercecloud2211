import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import {Component, Input, OnInit, Renderer2, ViewContainerRef} from '@angular/core';
import { FormBuilder, Validators } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { ActiveCartService } from '@spartacus/cart/base/core';
import { ActiveCartFacade, MultiCartFacade } from '@spartacus/cart/base/root';
import { CheckoutStepService } from '@spartacus/checkout/base/components';
import { CheckoutDeliveryAddressFacade,CheckoutQueryResetEvent } from '@spartacus/checkout/base/root';
import * as i1$1 from '@spartacus/core';
import { CommandService, EventService, GlobalMessageService, GlobalMessageType, OccEndpointsService, OCC_USER_ID_ANONYMOUS, PageLinkService, RoutingService, TranslationService, UserIdService } from '@spartacus/core';
import { OrderService } from '@spartacus/order/core';
import { OrderFacade } from '@spartacus/order/root';
import { LaunchDialogService } from '@spartacus/storefront';
import { BehaviorSubject, combineLatest } from 'rxjs';
import { catchError, filter, map, take } from 'rxjs/operators';
import { NovalnetService } from '../novalnet-service/novalnet-service';

@Component({
  selector: 'cx-place-order',
  templateUrl: './place-order.component.html',
})
export class NovalnetCheckoutPlaceOrderComponent implements OnInit{

    @Input() cxAtMessage: any;
    checkoutSubmitForm: any;
    routingService: i1$1.RoutingService;
    launchDialogService: LaunchDialogService;
    vcr: ViewContainerRef;
    placedOrder: any;
    http: HttpClient;
    occEndpoint: i1$1.OccEndpointsService;
    activeCartService: ActiveCartService;
    cartId: string;
    protected checkoutStepService: CheckoutStepService;
    activatedRoute: ActivatedRoute;
    novalnetService: NovalnetService;
    checkoutStepTypeReview: string;
    route: i1$1.RoutingService;
    pageLinkService: i1$1.PageLinkService;
    response : any;
    checkoutDeliveryAddressFacade: CheckoutDeliveryAddressFacade;
    deliveryAddress$: any;
    addressId: any;
    orderService: OrderService;
    orderDetails: any;
    renderer: Renderer2;
    globalMessageService: i1$1.GlobalMessageService;
    translation: i1$1.TranslationService;
    placeOrderCommand: any;
    commandService: i1$1.CommandService;
    placedOrder$: any;
    eventService: i1$1.EventService;
    userIdService: i1$1.UserIdService;
    activeCartFacade: ActiveCartFacade;
    multiCartFacade:MultiCartFacade;

    constructor(private fb: FormBuilder, private orderFacade: OrderFacade, routingService: RoutingService, launchDialogService: LaunchDialogService, vcr:ViewContainerRef, http:HttpClient, occEndpoint:OccEndpointsService, activeCartService: ActiveCartService, checkoutStepService: CheckoutStepService, activatedRoute:ActivatedRoute, novalnetService:NovalnetService,route:RoutingService, pageLinkService: PageLinkService, checkoutDeliveryAddressFacade: CheckoutDeliveryAddressFacade, orderService: OrderService, orderFacadde: OrderFacade, renderer: Renderer2, globalMessageService:GlobalMessageService, translation: TranslationService, commandService:CommandService, eventService : EventService, userIdService: UserIdService, activeCartFacade:ActiveCartFacade, multiCartFacade:MultiCartFacade){
        this.orderFacade = orderFacade;
        this.activatedRoute = activatedRoute;
        this.checkoutStepService = checkoutStepService;
        this.checkoutStepTypeReview = "ReviewOrder" /* CheckoutStepType.DELIVERY_MODE */;
        this.routingService = routingService;
        this.multiCartFacade = multiCartFacade;
        this.fb = fb;
        this.launchDialogService = launchDialogService;
        this.vcr = vcr;
        
        this.cartId = "";
        this.pageLinkService = pageLinkService;
        this.http = http;
        this.route = route;
        this.activeCartService = activeCartService;
        this.occEndpoint = occEndpoint;
        this.orderService = orderService;
        this.novalnetService = novalnetService;
        this.globalMessageService = globalMessageService;
        this.commandService = commandService;
        this.placedOrder$ = new BehaviorSubject(undefined);
        this.renderer =renderer;
        this.checkoutDeliveryAddressFacade = checkoutDeliveryAddressFacade;
        this.orderFacade = orderFacadde;
        this.activeCartFacade = activeCartFacade;
        this.userIdService = userIdService;
        this.translation = translation;
        this.eventService = eventService;
        this.checkoutSubmitForm = this.fb.group({
        termsAndConditions: [false, Validators.requiredTrue],
        
        });
        this.response = {};
        this.deliveryAddress$ = this.checkoutDeliveryAddressFacade
            .getDeliveryAddressState()
            .pipe(filter((state) => !state.loading), map((state) => state.data));

        this.activeCartService.getActiveCartId().subscribe((data) => {this.cartId = data});
        this.deliveryAddress$.subscribe((data:any) => {
            this.addressId = data.id;
        });
    }

    ngOnInit(): void {

        this.activatedRoute.queryParams .subscribe(params => { this.response = params });
        if(this.response.tid && this.response.status_code == "100" && this.response.status == "SUCCESS") {
            this.placedOrder = this.launchDialogService.launch("PLACE_ORDER_SPINNER" /* LAUNCH_CALLER.PLACE_ORDER_SPINNER */, this.vcr);
            let requestURL = this.occEndpoint.getBaseUrl() + "/novalnet/orders/placeOrder";
            var orderdata = {"payment_type": this.response.payment_type, "action": "get_order_details", "cartId" : this.cartId, "tid" : this.response.tid};
            this.http.post(requestURL, orderdata).subscribe((response:any) => {
                if(response.code) {
                    this.orderService.placeOrder = response;
                    this.orderService.setPlacedOrder(response);
                    this.novalnetService.setOrder(response);
                    this.orderFacade.setPlacedOrder(response);
                    this.eventService.dispatch({}, CheckoutQueryResetEvent);
                     this.multiCartFacade.removeCart(this.cartId);
                    this.routingService.go({ cxRoute: 'orderConfirmation' })
                } else {
                    this.globalMessageService.add("Unfortunately an error occured while placing order. Please try with another payment method",GlobalMessageType.MSG_TYPE_ERROR);
                    this.checkoutStepService.back(this.activatedRoute);
                }
            });
        } else if(this.response.status_text && this.response.tid) {
            this.globalMessageService.add(this.response.status_text,GlobalMessageType.MSG_TYPE_ERROR);
            this.checkoutStepService.back(this.activatedRoute);
        }
        
    }

    get termsAndConditionInvalid() {
        return this.checkoutSubmitForm.invalid;
    }

    ngAfterViewInit() {

        let data = this.novalnetService.getSelectedPaymentResponse();
        let address = this.novalnetService.getAddressDetails();

        let firstName, lastName, line1, line2, town, postalCode, country;
        firstName = lastName = line1 = line2 = town = postalCode = country = "";

        if(address && address.sameAsdelivery == false) {

            firstName = address.billingAddress.firstName;
            lastName = address.billingAddress.lastName;
            line1 = address.billingAddress.line1;
            line2 = address.billingAddress.line2;
            town = address.billingAddress.town;
            postalCode = address.billingAddress.postalCode;
            country = address.billingAddress.country.isoCode;
			
			if(document.getElementsByClassName('cx-review-card-payment').length > 0) {
				document.getElementsByClassName('cx-review-card-payment')[0].children[0].innerHTML += "<div class=cx-card ><div class = card-body cx-card-body> "+ " <span class='cx-card-title'> Payment Details </span><br/> Payment Name : "+data.payment_details.name+"<br><br><br><span class='cx-card-title'> Billing Details </span><br>"
				+ firstName + " " + lastName + "<br>"
				+ line1 + " " + line2 + "<br>"
				+ town + " " + country + "<br>"
				+ postalCode + "<br>"
				+"</div></div>";                                    
			}

        } else {

            this.deliveryAddress$.subscribe((addr:any) => {
                firstName = addr.firstName;
                lastName = addr.lastName;
                line1 = addr.line1;
                line2 = addr.line2;
                town = addr.town;
                postalCode = addr.postalCode;
                country = addr.country.name;
               
                if(document.getElementsByClassName('cx-review-card-payment').length > 0) {
					document.getElementsByClassName('cx-review-card-payment')[0].children[0].innerHTML += "<div class=cx-card ><div class = card-body cx-card-body> "+ " <span class='cx-card-title'> Payment Details </span><br/> Payment Name : "+data.payment_details.name+"<br><br><br><span class='cx-card-title'> Billing Details </span><br>"
					+ firstName + " " + lastName + "<br>"
					+ line1 + " " + line2 + "<br>"
					+ town + " " + country + "<br>"
					+ postalCode + "<br>"
					+"</div></div>";
				}
            });
            
        }     
    }

    submitForm() {

        if (this.checkoutSubmitForm.valid) {
            this.placedOrder = this.launchDialogService.launch("PLACE_ORDER_SPINNER" /* LAUNCH_CALLER.PLACE_ORDER_SPINNER */, this.vcr);
            let data = this.novalnetService.getSelectedPaymentResponse();
            let address = this.novalnetService.getAddressDetails();
            var url = this.route.getFullUrl({ cxRoute: 'checkoutReviewOrder'});

            if(data) {

                var path = (data.payment_details.process_mode == "direct") ? "/novalnet/orders/placeOrder" : "/novalnet/orders/getRedirectURL";
                var paymentdata = {"paymentData": data, "returnUrl" : "" , "cartId" : this.cartId, "address" : address, "action": "create_order"};
                if(data.payment_details.process_mode == "redirect" || (data.booking_details && data.booking_details.do_redirect == true)) {
                    paymentdata["returnUrl"] = url;
                }
                let requestURL = this.occEndpoint.getBaseUrl()+ path;
                console.log('requestURL' + requestURL);
                
                this.sendPlaceOrderRequest(requestURL, paymentdata).subscribe(
					(response:any) => {
						if(response != null && typeof response != 'undefined') {
							if((data.payment_details.process_mode == "direct") && response.code) {
								this.orderService.placeOrder = response;
								this.orderService.setPlacedOrder(response);
								this.orderFacade.setPlacedOrder(response);
								this.novalnetService.setOrder(response);
								this.eventService.dispatch({}, CheckoutQueryResetEvent);
								this.multiCartFacade.removeCart(this.cartId);
								this.routingService.go({ cxRoute: 'orderConfirmation' });
							}
							else if(response.redirectURL) {
								window.location.href= response.redirectURL;
							}
							else {
								this.handleAjaxFailure();
							}
						}
						else {
							this.handleAjaxFailure();
						}
					},
					error => {
						this.handleAjaxFailure();
					}
				);
            } else {
                this.globalMessageService.add("Payment details are not valid",GlobalMessageType.MSG_TYPE_ERROR);
                this.checkoutStepService.back(this.activatedRoute);
            }
        }else {
			console.log('forminvalid');
            this.checkoutSubmitForm.markAllAsTouched();
        }
    }

    sendPlaceOrderRequest(requestURL: string, paymentdata: { paymentData: any; returnUrl: string; cartId: string | undefined; address: {} | undefined; action: string; }) {
        return this.http.post(requestURL, paymentdata);
    }

    ngOnDestroy() {
        this.launchDialogService.clear("PLACE_ORDER_SPINNER" /* LAUNCH_CALLER.PLACE_ORDER_SPINNER */);
    }
    handleAjaxFailure() {
		this.launchDialogService.clear("PLACE_ORDER_SPINNER" /* LAUNCH_CALLER.PLACE_ORDER_SPINNER */);
		this.globalMessageService.add("Unfortunately an error occured while placing order. Please try with another payment method",GlobalMessageType.MSG_TYPE_ERROR);
		this.checkoutStepService.back(this.activatedRoute);
    }

}

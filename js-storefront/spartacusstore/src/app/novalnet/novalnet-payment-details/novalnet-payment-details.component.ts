import { HttpClient } from '@angular/common/http';
import { Component, Renderer2, ViewContainerRef, ViewEncapsulation, HostListener } from '@angular/core';
import { FormBuilder, Validators } from '@angular/forms';
import { CheckoutDeliveryAddressFacade } from '@spartacus/checkout/base/root';
import { Country, GlobalMessageService, GlobalMessageType, HttpErrorHandler, OccEndpointsService, UserAddressService, UserPaymentService } from '@spartacus/core';
import { BehaviorSubject, combineLatest, Observable, Observer } from 'rxjs';
import { filter, map, switchMap, tap } from 'rxjs/operators';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { ScriptService } from "./services/script.service";
import { ActiveCartService } from '@spartacus/cart/base/core';
import { NovalnetService } from '../novalnet-service/novalnet-service';
import { CheckoutStepService } from '@spartacus/checkout/base/components';
import { ActivatedRoute } from '@angular/router';
import { LaunchDialogService } from '@spartacus/storefront';

const SCRIPT_PATH = 'https://cdn.novalnet.de/js/pv13/checkout.js?'+Math.floor(Math.random()*999);
declare let NovalnetPaymentForm: any;

@Component({
  selector: 'app-novalnet-payment-details',
  templateUrl: './novalnet-payment-details.component.html',
  styleUrls: ['./novalnet-payment-details.component.scss'],
  encapsulation : ViewEncapsulation.None
})
export class NovalnetPaymentDetailsComponentComponent {


  userPaymentService: UserPaymentService;
  countries$: Observable<Country[]> | undefined;
  deliveryAddress$: Observable<any>;
  checkoutDeliveryAddressFacade: CheckoutDeliveryAddressFacade;
  sameAsDeliveryAddress: boolean | undefined;
  billingaddressEdited: boolean | undefined;
  showSameAsDeliveryAddressCheckbox$: Observable<boolean> | undefined;
  fb: FormBuilder;
  billingAddressForm: any;
  paymentForm: any;
  selectedCountry$: BehaviorSubject<string>;
  
  userAddressService: UserAddressService;
  regions$: Observable<import("@spartacus/core").Region[]> | undefined;
  showPayments$: any;
  paymentResponse: any;
  occEndpoint: OccEndpointsService;
  http: HttpClient;
  payment: any;
  description: any;
  active: any;
  currentChecked: any;
  divText: any;
  loadPaymentform: boolean;
  showBillingFormEdited: boolean;
  showBillingEditForm: boolean;
  url: string;
  sanitizer: DomSanitizer;
  urlSafe: SafeResourceUrl | undefined;
  scriptService: ScriptService;
  renderer: Renderer2;
  addressId: String;
  cartId: any;
  activeCartService: ActiveCartService;
  paymentURL: any;
  htmliframe: string;
  loadPaymentformtest:Observable<boolean> | undefined;
  userPreferences$:Observer<boolean> | undefined;
  novalnetpaymentForm: any;
  novalnetService: NovalnetService;
  checkoutStepService: CheckoutStepService;
  activatedRoute: ActivatedRoute;
  errorHandler: HttpErrorHandler;
  globalMessageService: GlobalMessageService;
  launchDialogService: LaunchDialogService;
  vcr: ViewContainerRef;
  listener: (() => void) | undefined;

  constructor(userPaymentService: UserPaymentService, checkoutDeliveryAddressFacade: CheckoutDeliveryAddressFacade, fb: FormBuilder, userAddressService: UserAddressService, occEndpoint:OccEndpointsService,  http:HttpClient,  sanitizer: DomSanitizer, scriptService: ScriptService, renderer: Renderer2, activeCartService: ActiveCartService, novalnetService: NovalnetService, checkoutStepService: CheckoutStepService, activatedRoute :ActivatedRoute, errorHandler: HttpErrorHandler, globalMessageService:GlobalMessageService, launchDialogService: LaunchDialogService, vcr : ViewContainerRef) {
    this.htmliframe = "";
    this.userPaymentService = userPaymentService;
    this.userAddressService = userAddressService;
    this.activeCartService = activeCartService;
    this.novalnetService = novalnetService;
    this.checkoutStepService = checkoutStepService;
    this.activatedRoute = activatedRoute;
    this.launchDialogService =launchDialogService;
    this.vcr = vcr;
    this.fb = fb;
    this.checkoutDeliveryAddressFacade = checkoutDeliveryAddressFacade;
    this.selectedCountry$ = new BehaviorSubject('');
    this.occEndpoint = occEndpoint;
    this.loadPaymentform = false;
    this.billingaddressEdited = false;
    this.showBillingFormEdited = false;
    this.showBillingEditForm = false;
    this.http = http;
    this.scriptService = scriptService;
    this.renderer = renderer;
    this.url = "";
    this.paymentResponse = {};
    this.errorHandler = errorHandler;
    this.sanitizer = sanitizer;
    this.globalMessageService = globalMessageService;
    this.billingAddressForm = this.fb.group({
        firstName: ['', Validators.required],
        lastName: ['', Validators.required],
        line1: ['', Validators.required],
        line2: [''],
        town: ['', Validators.required],
        region: this.fb.group({
            isocodeShort: [null, Validators.required],
        }),
        country: this.fb.group({
            isocode: [null, Validators.required],
        }),
        postalCode: ['', Validators.required],
    });

    this.paymentForm = this.fb.group({
      paymentName: ['', Validators.required],
    }); 

    this.addressId = "";

    const scriptElement = this.scriptService.loadJsScript(this.renderer, SCRIPT_PATH);

    

    this.deliveryAddress$ = this.checkoutDeliveryAddressFacade
    .getDeliveryAddressState()
    .pipe(filter((state) => !state.loading), map((state) => state.data));
    
  }
  
  
  ngOnInit () {
    
    this.getPaymentInfo().subscribe((response: any) => this.paymentResponse = response);

    this.regions$ = this.selectedCountry$.pipe(switchMap((country) => this.userAddressService.getRegions(country)), tap((regions) => {
        const regionControl = this.billingAddressForm.get('region.isocodeShort');
        if (regions.length > 0) {
            regionControl?.enable();
        }
        else {
            regionControl?.disable();
        }
    }));

    this.countries$ = this.userPaymentService.getAllBillingCountries().pipe(tap((countries) => {
        // If the store is empty fetch countries. This is also used when changing language.
        if (Object.keys(countries).length === 0) {
            this.userPaymentService.loadBillingCountries();
        }
    }));

    this.showSameAsDeliveryAddressCheckbox$ = combineLatest([
        this.countries$,
        this.deliveryAddress$,
    ]).pipe(map(([countries, address]) => {
        return ((address?.country &&
            !!countries.filter((country) => country.isocode === address.country?.isocode).length) ??
            false);
    }), tap((shouldShowCheckbox) => {
        this.sameAsDeliveryAddress = shouldShowCheckbox;
    }));
  }

  getPaymentInfo() {
    let url = this.occEndpoint.getBaseUrl()+"/novalnet/config/details";
    return this.http.get(url);
  }

  setchecked(event: any) {
    this.currentChecked = event.target.value;
    this.divText = this.paymentResponse.paymentinfo[this.currentChecked].description;
  }

  next() {
    if(!this.sameAsDeliveryAddress) {
      this.billingaddressEdited = true;
    }
  }

  back() {
    this.checkoutStepService.back(this.activatedRoute);
  }

  toggleSameAsDeliveryAddress() {
    this.sameAsDeliveryAddress = !this.sameAsDeliveryAddress;
    if(this.loadPaymentform) {

      this.loadPaymentform = !this.loadPaymentform;
    }
  }

  loadiframe() {

    this.novalnetpaymentForm = new NovalnetPaymentForm();
    let paymentFormRequestObj = {
      iframe : '#novalnetPaymentIFrame',
      initForm: {
        creditCard: {
            text : {
                error: "Your credit card details are invalid",
                card_holder: {
                    label: "Card holder name",
                    place_holder: "Name on Card",
                    error: "Please enter the valid card holder name"
                },
                card_number: {
                    label: "Card number",
                    place_holder: "XXXX XXXX XXXX XXXX",
                    error: "Please enter the valid card number"
                },
                expiry_date: {
                    label: "Expiry date",
                    error: "Please enter the valid expiry month / year in the given format"
                },
                cvc: {
                    label: "CVC/CVV/CID",
                    place_holder: "XXX",
                    error: "Please enter the valid CVC/CVV/CID"
                }
            }
        },
        uncheckPayments: true,
        showButton: false,
      }
    };
    // initiate form
    this.novalnetpaymentForm.initiate(paymentFormRequestObj);
    this.novalnetpaymentForm.walletResponse({
      onProcessCompletion: async (response:any) => {
          if(response.result.status != "ERROR") {
            this.novalnetService.setSelectedPaymentResponse(response);
            this.checkoutStepService.next(this.activatedRoute);
            return {status : "SUCCESS", statusText : ''};
          } else {
            this.globalMessageService.add(response.result.message,GlobalMessageType.MSG_TYPE_ERROR);
            return {status : "FAILURE", statusText : ''};
          }
      }     
    });
  }

  async showPaymentForm() {

    if(!this.loadPaymentform) {
      if (this.billingAddressForm.valid || this.sameAsDeliveryAddress) {
        this.loadPaymentform = !this.loadPaymentform;
        this.geturl().then((response:any) =>  {
          const element =  document.getElementById("novalnetPaymentIFrame");
          if (element !== null) {
            element.setAttribute("src", response);
            this.loadiframe();
          }
        })
      } else {
          this.billingAddressForm.markAllAsTouched();
      }
    } else {
      this.novalnetpaymentForm.getPayment( (response:any) => {
		  console.log(response);
        if(response.result.status != "ERROR") {
          this.novalnetService.setSelectedPaymentResponse(response);
          this.checkoutStepService.next(this.activatedRoute);
        } else {
          this.globalMessageService.add(response.result.message,GlobalMessageType.MSG_TYPE_ERROR);
        }
      });
    } 
  }

  async geturl() {

    let request = {};
    this.activeCartService.getActiveCartId().subscribe((data) => {this.cartId = data});
    if(!this.sameAsDeliveryAddress) {
        this.showBillingFormEdited = true;
        this.deliveryAddress$.subscribe((data) => {
          this.addressId = data.id;
        });
        let region = (this.billingAddressForm.get('region.isocodeShort').value)? this.billingAddressForm.get('region.isocodeShort').value + ', ' : "";
        request = {"sameAsdelivery": false, "billingAddress" : {"firstName" : this.billingAddressForm.get('firstName').value, "lastName" : this.billingAddressForm.get('lastName').value, "line1" : this.billingAddressForm.get('line1').value, "line2" : this.billingAddressForm.get('line2').value, "postalCode" : this.billingAddressForm.get('postalCode').value, "town" : this.billingAddressForm.get('town').value, "country" :{ "isoCode" : this.billingAddressForm.get('country.isocode').value}, "addressId": this.addressId}, "cartId" : this.cartId };
    } else {
        this.deliveryAddress$.subscribe((data) => {
          this.addressId = data.id;
        });
        request = {"sameAsdelivery": true, "billingAddress" : {"addressId": this.addressId}, "cartId" : this.cartId };
    }
    let requestURL = this.occEndpoint.getBaseUrl()+"/novalnet/getPaymentURL";
    this.novalnetService.setAddresDetails(request);
    this.url = await new Promise<any>(resolve =>  this.http.post(requestURL, request).subscribe( (res:any) => { resolve(res.paymentURL)}));
    this.urlSafe= this.sanitizer.bypassSecurityTrustResourceUrl(this.url);
    return Promise.resolve(this.url);
  }

  getAddressCardContent(address: { region: { isocode: string; }; firstName: string; lastName: string; line1: any; line2: any; town: string; country: { isocode: string; }; postalCode: any; phone: any; }) {
    let region = '';
    if (address.region && address.region.isocode) {
        region = address.region.isocode + ', ';
    }
    return {
        textBold: address.firstName + ' ' + address.lastName,
        text: [
            address.line1,
            address.line2,
            address.town + ', ' + region + address.country?.isocode,
            address.postalCode,
            address.phone,
        ],
    };
  }

  getBillingAddressCardContent() {
   let region = (this.billingAddressForm.get('region.isocodeShort').value)? this.billingAddressForm.get('region.isocodeShort').value + ', ' : "";
   return {
       textBold: this.billingAddressForm.get('firstName').value + ' ' + this.billingAddressForm.get('lastName').value,
       text: [
        this.billingAddressForm.get('line1').value,
        this.billingAddressForm.get('line2').value,
        this.billingAddressForm.get('town').value + ', ' + region + this.billingAddressForm.get('country.isocode').value,
        this.billingAddressForm.get('postalCode').value,
       ],
    };
  }

  setvalues(payment:any,data:any) {
    this.payment = payment;
    this.description = data.description;
    this.active = data.active;
  }

  editBilling() {
    this.showBillingEditForm = ! this.showBillingEditForm;
    this.showBillingFormEdited = ! this.showBillingFormEdited;
    this.loadPaymentform = !this.loadPaymentform;
  }

  countrySelected(country: { isocode: any; }) {
      this.billingAddressForm.get('country.isocode')?.setValue(country.isocode);
      this.selectedCountry$.next(country.isocode);
  }

}

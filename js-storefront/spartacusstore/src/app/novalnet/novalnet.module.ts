import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { CmsConfig, ConfigModule, I18nModule, provideConfig, UrlModule} from '@spartacus/core';
import { NovalnetPaymentDetailsComponentComponent } from './novalnet-payment-details/novalnet-payment-details.component';
import { AtMessageModule, CardModule, FormErrorsModule, NgSelectA11yModule, OutletModule, PromotionsModule, PwaModule, SpinnerModule } from '@spartacus/storefront';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { NgSelectModule } from '@ng-select/ng-select';
import { CartNotEmptyGuard, CheckoutAuthGuard, CheckoutProgressComponent, CheckoutProgressMobileBottomComponent, CheckoutProgressMobileTopComponent } from '@spartacus/checkout/base/components';
import { NovalnetCheckoutPlaceOrderComponent } from './novalnet-place-order-component/place-order-component';
import { RouterModule } from '@angular/router';
import { OrderModule } from '@spartacus/order';
import { OrderConfirmationComponent } from './order-confirmation/order-confirmation.component';
import { NovalnetorderDetailsComponent } from './novalnet-order-details/novalnet-order-details.component';
import { OrderComponentsModule, OrderConfirmationModule, OrderGuestRegisterFormComponent} from '@spartacus/order/components';

@NgModule({
  declarations: [ NovalnetPaymentDetailsComponentComponent, NovalnetCheckoutPlaceOrderComponent, OrderConfirmationComponent, NovalnetorderDetailsComponent ],
  imports: [
    CommonModule,
    I18nModule,
    CardModule,
    FormsModule,
    FormErrorsModule,
    NgSelectModule,
    NgSelectA11yModule,
    SpinnerModule,
    ReactiveFormsModule,
    SpinnerModule,
    AtMessageModule,
    RouterModule,
    UrlModule,
    OrderModule,
    OrderConfirmationModule,
    PwaModule,
    PromotionsModule,
    OutletModule,
    ConfigModule.withConfig({
      cmsComponents : {
        CheckoutPaymentDetails : {
          component : NovalnetPaymentDetailsComponentComponent,
        },
        CheckoutProgressMobileBottom: {
          component: CheckoutProgressMobileBottomComponent,
          guards: [CheckoutAuthGuard, CartNotEmptyGuard],
        },
        CheckoutProgress: {
            component: CheckoutProgressComponent,
            guards: [CheckoutAuthGuard, CartNotEmptyGuard],
        },
        CheckoutProgressMobileTop: {
          component: CheckoutProgressMobileTopComponent,
          guards: [CheckoutAuthGuard, CartNotEmptyGuard],
        },
        CheckoutPlaceOrder: {
          component : NovalnetCheckoutPlaceOrderComponent,
        },
        OrderConfirmationThankMessageComponent :{
          component : OrderConfirmationComponent,
        },
        AccountOrderDetailsActionsComponent :{
          component : NovalnetorderDetailsComponent,
        },
      }
    } as CmsConfig),
  ],
  exports:[],
  providers: [
    provideConfig({
      routing: {
        routes: {
          checkout: {
            paths: ['checkout'],
          },
          checkoutShippingAddress: {
            paths: ['checkout/shipping-address']
          },
          checkoutDeliveryMode: {
            paths: ['checkout/delivery-mode']
          },
          checkoutPaymentDetails: {
            paths: ['checkout/payment-details']
          },
          checkoutReviewOrder: {
            paths: ['checkout/review-order']
          }
        },
      }
    }),
    
  ],
})

export class NovalnetModule {

}

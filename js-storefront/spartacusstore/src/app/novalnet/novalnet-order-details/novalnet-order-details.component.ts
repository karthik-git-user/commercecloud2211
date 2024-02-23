import { HttpClient } from '@angular/common/http';
import { Component } from '@angular/core';
import { OccEndpointsService } from '@spartacus/core';
import { OrderDetailsService } from '@spartacus/order/components';
import { Order } from '@spartacus/order/root';
import { observable, Observable } from 'rxjs';

@Component({
  selector: 'cx-order-details-actions',
  templateUrl: './novalnet-order-details.component.html',
  styleUrls: ['./novalnet-order-details.component.scss']
})
export class NovalnetorderDetailsComponent {

 orderDetailsService: OrderDetailsService;
  order$: Observable<Order>;
  occEndpoint: OccEndpointsService;
  http: HttpClient;
  orderData: any;
  done:Boolean;

  constructor(orderDetailsService:OrderDetailsService, occEndpoint:OccEndpointsService, http:HttpClient) {
    this.orderDetailsService = orderDetailsService;
    this.order$ = new Observable<Order>
    this.occEndpoint = occEndpoint;
    this.http = http;
    this.done = false;
  }
  
  ngOnInit() {
    this.order$ = this.orderDetailsService.getOrderDetails();
   
  }

  appendTxnDetails(orderNo:string|undefined) {
    if(orderNo != "" && !this.done) {
      this.done = true;
      let requestURL = this.occEndpoint.getBaseUrl()+ "/novalnet/orders/paymentDetails?orderno="+orderNo;
      this.http.post(requestURL, {}).subscribe((data:any) => {
      document.getElementsByClassName('nn_txn_message')[0].innerHTML = data.comments;});
    }
  }


}

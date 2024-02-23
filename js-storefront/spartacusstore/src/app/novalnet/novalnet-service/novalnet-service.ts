import { Injectable } from '@angular/core';

@Injectable({
  providedIn: 'root'
})

export class NovalnetService {
  
  paymentTypeResponse: any;
  addressDetails : any;
  order: any;

  setSelectedPaymentResponse(data : any){
    this.paymentTypeResponse = data;
  }

  getSelectedPaymentResponse(){
    return this.paymentTypeResponse;
  }

  setOrder(order: any) {
    this.order = order;
  }

  getOrder() {
    return this.order;
  }

  setAddresDetails(request: {}) {
    this.addressDetails = request;
  }

  getAddressDetails() {
    return this.addressDetails;
  }

  
  }
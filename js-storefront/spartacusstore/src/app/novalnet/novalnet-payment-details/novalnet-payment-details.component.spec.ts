import { ComponentFixture, TestBed } from '@angular/core/testing';

import { NovalnetPaymentDetailsComponentComponent } from './novalnet-payment-details.component';

describe('NovalnetPaymentDetailsComponentComponent', () => {
  let component: NovalnetPaymentDetailsComponentComponent;
  let fixture: ComponentFixture<NovalnetPaymentDetailsComponentComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ NovalnetPaymentDetailsComponentComponent ]
    })
    .compileComponents();

    fixture = TestBed.createComponent(NovalnetPaymentDetailsComponentComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});

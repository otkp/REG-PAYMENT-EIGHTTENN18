package org.epragati.payments.service;

import org.epragati.payments.service.impl.CFMSGateWay;
import org.epragati.payments.service.impl.PayUGateWay;
import org.epragati.payments.service.impl.SBIGateWay;
import org.epragati.util.payment.GatewayTypeEnum;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * 
 * @author naga.pulaparthi
 *
 */
@Component
public class PaymentGatewayFactoryProvider {

	@Autowired
	@Qualifier("sbiGateway")
	private SBIGateWay sbiGateway;

	@Autowired
	@Qualifier("payUGateWay")
	private PayUGateWay payUGateWay;

	@Autowired
	@Qualifier("cfmsGateWay")
	private CFMSGateWay cfmsGateWay;

	public PaymentGateWay getPaymentGateWayInstance(GatewayTypeEnum payGatewayTypeEnum) {
		if (payGatewayTypeEnum.equals(GatewayTypeEnum.SBI)) {
			return sbiGateway;
		} else if (payGatewayTypeEnum.equals(GatewayTypeEnum.PAYU)) {
			return payUGateWay;
		} else if (payGatewayTypeEnum.equals(GatewayTypeEnum.CFMS)) {
			return cfmsGateWay;
		}
		return sbiGateway;
	}
}

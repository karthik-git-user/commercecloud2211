/*
 * Copyright (c) 2020 SAP SE or an SAP affiliate company. All rights reserved.
 */
package novalnet.controllers;

import de.hybris.platform.webservicescommons.errors.exceptions.WebserviceException;

/**
 * Thrown when an operation is performed that requires a session cart, which was not yet created.
 */
public class NovalnetPaymentException extends WebserviceException
{
	public static final String INVALID = "invalid";
	public static final String MISSING = "missing";
	public static final String UNKNOWN_IDENTIFIER = "unknownIdentifier";
	private static final String TYPE = "ValidationError";
	private static final String SUBJECT_TYPE = "parameter";

	/**
	 * @param message
	 */
	public NovalnetPaymentException(String message)
	{
		super(message);
	}

	@Override
	public String getType()
	{
		return TYPE;
	}

	@Override
	public String getSubjectType()
	{
		return SUBJECT_TYPE;
	}
}
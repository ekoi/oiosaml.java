/*
 * The contents of this file are subject to the Mozilla Public 
 * License Version 1.1 (the "License"); you may not use this 
 * file except in compliance with the License. You may obtain 
 * a copy of the License at http://www.mozilla.org/MPL/
 * 
 * Software distributed under the License is distributed on an 
 * "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, either express 
 * or implied. See the License for the specific language governing
 * rights and limitations under the License.
 *
 *
 * The Original Code is OIOSAML Java Service Provider.
 * 
 * The Initial Developer of the Original Code is Trifork A/S. Portions 
 * created by Trifork A/S are Copyright (C) 2008 Danish National IT 
 * and Telecom Agency (http://www.itst.dk). All Rights Reserved.
 * 
 * Contributor(s):
 *   Joakim Recht <jre@trifork.com>
 *   Rolf Njor Jensen <rolf@trifork.com>
 *
 */
package dk.itst.oiosaml.sp.service.util;

import java.lang.reflect.Constructor;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import javax.servlet.ServletContext;

import org.apache.commons.configuration.Configuration;
import org.opensaml.ws.soap.util.SOAPConstants;
import org.opensaml.xml.util.Base64;

import dk.itst.oiosaml.common.OIOSAMLConstants;
import dk.itst.oiosaml.error.Layer;
import dk.itst.oiosaml.error.WrappedException;
import dk.itst.oiosaml.sp.service.SAMLHandler;

/**
 * Utility class used for signing SAML documents and verifying the signed
 * documents received from the Login Site
 * 
 */
public final class Utils {

	public static final String VERSION = "$Id: Utils.java 3197 2008-07-25 07:47:33Z jre $";
	private static final Logger log = Logger.getLogger(Utils.class);
	private static final String[] SOAP_VERSIONS = new String[] { SOAPConstants.SOAP11_NS, SOAPConstants.SOAP12_NS};


	/**
	 * Making nice XML for output in browser, i.e. converting &lt; to &amp;lt;, &gt; to
	 * &amp;gt; etc.
	 */
	public static String makeXML(String param) {
		String xml = param;
		if (xml != null && !"".equals(xml)) {
			xml = xml.replaceAll("><", ">\n<");
			xml = xml.replaceAll("<", "&lt;");
			xml = xml.replaceAll(">", "&gt;");
			xml = xml.replaceAll("\n", "<br />");
		}
		return xml;
	}

	/**
	 * Encode a string to html entities.
	 */
	public static String htmlEntityEncode(String s) {
		StringBuilder buf = new StringBuilder();
		int len = (s == null ? -1 : s.length());
		for (int i = 0; i < len; i++) {
			char c = s.charAt(i);
			if (c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9') {
				buf.append(c);
			} else {
				buf.append("&#" + (int) c + ";");
			}
		}
		return buf.toString();
	} 
	
	/**
	 * @return true if the queryString in the request has been signed correctly
	 *         by the Login Site
	 */
	public static boolean verifySignature(String signature, String queryString, String queryParameter, PublicKey publicKey) {
		// Verifying the signature....
		if (log.isDebugEnabled())
			log.debug("signature..:" + signature);
		if (signature == null) {
			return false;
		}

		byte[] buffer = Base64.decode(signature);
		
		String data = parseSignedQueryString(queryString, queryParameter);

//        String data = queryString.substring(queryString.indexOf(firstQueryParameter), queryString.lastIndexOf("&"));
		if (log.isDebugEnabled())
			log.debug("data.......:" + data);

		if (log.isDebugEnabled())
			log.debug("Verifying Signature...");
		
		return verifySignature(data.getBytes(), publicKey, buffer);
	}

    /**
	 * Check if a SAML HTTP Redirect has been signed by the expected certificate
	 * 
	 * @param data
	 *            The query parameters in the HTTP Redirect, which has been
	 *            signed
	 * @param key
	 *            The public key of the certificate from the expected sender
	 * @param sig
	 *            The signature generated by the sender after it has been base64
	 *            decoded
	 * @return true, if the signature is valid, otherwise false
	 */
	public static boolean verifySignature(byte[] data, PublicKey key, byte[] sig) {

		if (log.isDebugEnabled())
			log.debug("data...:" + new String(data));
		if (log.isDebugEnabled())
			log.debug("sig....:" + new String(sig));
		if (log.isDebugEnabled())
			log.debug("key....:" + key.toString());

		try {
			Signature signer = Signature.getInstance(OIOSAMLConstants.SHA1_WITH_RSA);
			signer.initVerify(key);
			signer.update(data);
			return signer.verify(sig);
		} catch (InvalidKeyException e) {
			throw new WrappedException(Layer.CLIENT, e);
		} catch (NoSuchAlgorithmException e) {
			throw new WrappedException(Layer.CLIENT, e);
		} catch (SignatureException e) {
			throw new WrappedException(Layer.CLIENT, e);
		}
	}

	/**
	 * @return A beautified xml string
	 */
	public static String beautifyAndHtmlXML(String xml, String split) {
		return makeXML(beautifyXML(xml, split));
	}

	/**
	 * @return A beautified xml string
	 */
	public static String beautifyXML(String xml, String split) {
		String s = "";
		if (split != null)
			s = ".:split:.";

		if (xml == null || "".equals(xml))
			return xml;

		StringBuffer result = new StringBuffer();

		
		String[] results = xml.split("<");
		for (int i = 1; i < results.length; i++) {
			results[i] = "<" + results[i].trim();
			if (results[i].endsWith("/>")) {
				result.append(results[i]).append(s);
			} else if (results[i].startsWith("</")) {
				result.append(results[i]).append(s);
			} else if (results[i].endsWith(">")) {
				result.append(results[i]).append(s);
			} else {
				result.append(results[i]);
			}
		}
//		result = result.trim();

		if (split == null)
			return result.toString().trim();

		StringBuilder newResult = new StringBuilder();
		String ident = "";
		results = result.toString().split(s);
		for (int i = 0; i < results.length; i++) {
			if (results[i].startsWith("</"))
				ident = ident.substring(split.length());

			newResult.append(ident).append(results[i]).append("\n");

			if (!results[i].startsWith("<!") && !results[i].startsWith("<?")
					&& results[i].indexOf("</") == -1
					&& results[i].indexOf("/>") == -1)
				ident += split;
		}
		return newResult.toString();
	}
	
	/**
	 * Generate a valid xs:ID string.
	 */
	public static String generateUUID() {
		return "_" + UUID.randomUUID().toString();
	}
	
	/**
	 * Get the SOAP version from an Envelope.
	 * @param xml The complete envelope as a String.
	 * @return The SOAP version, represented by the SOAP namespace. Returns <code>null</code> if no namespace was found.
	 */
	public static String getSoapVersion(String xml) {
	
		for (int i = 0; i < SOAP_VERSIONS.length; i++) {
			int idx = xml.indexOf(SOAP_VERSIONS[i]);
			if (idx > -1) {
				String prefix = getPrefix(xml, idx);
				int start = xml.lastIndexOf('<', idx);

				if (prefix == null) {
					prefix = "<";
				} else {
					prefix = "<" + prefix + ":";
				}
				if (xml.lastIndexOf(prefix + "Envelope", idx) >= start) {
					return SOAP_VERSIONS[i];
				}
			}
		}
		return null;
	}
	
	private static String getPrefix(String xml, int idx) {
		if (idx > -1) {
			String prefix = xml.substring(xml.lastIndexOf(' ', idx) + 1, idx);
			if (prefix.startsWith("xmlns:")) {
				prefix = prefix.substring(6, prefix.lastIndexOf('=')).trim();
			} else {
				prefix = null;
			}
			return prefix;
		}
		return null;
	}

	public static Object newInstance(Configuration cfg, String property) {
		String name = cfg.getString(property);
		if (name == null) {
			throw new IllegalArgumentException("Property " + property + " has not been set");
		}
		
		try {
			Class<?> c = Class.forName(name);
			
			return c.newInstance();
		} catch (Exception e) {
			throw new RuntimeException("Unable to create instance of " + name, e);
		}
	}
	
	public static Map<String, SAMLHandler> getHandlers(Configuration config, ServletContext servletContext) {
		Map<String, SAMLHandler> handlers = new HashMap<String, SAMLHandler>();
		
		for (Iterator<?> i = config.getKeys(); i.hasNext();) {
			String key = (String) i.next();
			if (!key.startsWith("oiosaml-sp.protocol.endpoints.")) continue;
			log.debug("Checking " + key);
			
			try {
				Class<?> c = Class.forName(config.getString(key));
				SAMLHandler instance;
				try {
					Constructor<?> constructor = c.getConstructor(Configuration.class);
					instance = (SAMLHandler) constructor.newInstance(config);
				} catch (NoSuchMethodException e) {
					try {
					Constructor<?> constructor = c.getConstructor(ServletContext.class);
					instance = (SAMLHandler) constructor.newInstance(servletContext);
					} catch (NoSuchMethodException ex) {
						instance = (SAMLHandler) c.newInstance();
					}
				}
				
				handlers.put(key.substring(key.lastIndexOf('.') + 1), instance);

			} catch (Exception e) {
				log.error("Unable to instantiate " + key + ": " + config.getString(key), e);
				throw new RuntimeException(e);
			}
			
		}
		
		return handlers;
	}
	
    public static String parseSignedQueryString(String queryString, String queryParameter) {
        StringBuilder s = new StringBuilder();
        
        String samlRequestOrResponse = Utils.getParameter(queryParameter, queryString);
        String relayState = Utils.getParameter(Constants.SAML_RELAYSTATE, queryString);
        String sigAlg = Utils.getParameter(Constants.SAML_SIGALG, queryString);

        // see order in http://docs.oasis-open.org/security/saml/v2.0/saml-bindings-2.0-os.pdf line 601->605
        s.append(queryParameter);
        s.append("=");
        s.append(samlRequestOrResponse);
        if(relayState != null) {
            s.append("&");
            s.append(Constants.SAML_RELAYSTATE);
            s.append("=");
            s.append(relayState);
        }
        s.append("&");
        s.append(Constants.SAML_SIGALG);
        s.append("=");
        s.append(sigAlg);

        return s.toString();
    }

    
    public static String getParameter(String name, String url) {
        int qpos = url.indexOf('?') + 1;
        String[] parts = url.substring(qpos).split("&");
        for (String part : parts) {
            int pos = part.indexOf('=');
            String key = part.substring(0, pos);
            if (name.equals(key)) {
                return part.substring(pos + 1);
            }
        }
        return null;
    }

}

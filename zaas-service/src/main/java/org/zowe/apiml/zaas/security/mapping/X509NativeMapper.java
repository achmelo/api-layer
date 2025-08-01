/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.apiml.zaas.security.mapping;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.zowe.apiml.zaas.security.service.schema.source.AuthSource;
import org.zowe.apiml.zaas.security.service.schema.source.X509AuthSource;
import org.zowe.commons.usermap.CertificateResponse;

import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;

@Slf4j
@RequiredArgsConstructor
@Component("x509Mapper")
@ConditionalOnProperty(value = "apiml.security.useInternalMapper", havingValue = "true")
public class X509NativeMapper implements AuthenticationMapper {

    private final NativeMapperWrapper nativeMapper;

    @Override
    public String mapToMainframeUserId(AuthSource authSource) {
        if (authSource instanceof X509AuthSource) {
            X509AuthSource x509AuthSource = (X509AuthSource)authSource;
            X509Certificate certificate = x509AuthSource.getRawSource();
            if (certificate != null) {
                try {
                   CertificateResponse response = nativeMapper.getUserIDForCertificate(certificate.getEncoded());
                   var userId = response.getUserId();
                   log.debug("Certificate {} mapped to the user {}", certificate.getSubjectX500Principal().getName(), userId);
                   if (response.getRc() == 0 && StringUtils.isNotEmpty(userId)) {
                       return userId;
                   }
                } catch (CertificateEncodingException e) {
                    log.error("Can`t get encoded data from certificate", e);
                }
            } else {
                log.warn("No certificate found in the authentication source.");
            }
        } else {
            log.debug("The used authentication source type is {} and not X509", authSource.getType());
        }
        return null;
    }
}

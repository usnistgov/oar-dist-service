package gov.nist.oar.distrib.service.rpa;

import gov.nist.oar.distrib.service.rpa.model.UserInfo;
import gov.nist.oar.distrib.service.rpa.model.UserInfoWrapper;

import static org.apache.commons.lang3.StringEscapeUtils.escapeHtml4;

public class HTMLCleaner {

    public static UserInfoWrapper clean(UserInfoWrapper userInfoWrapper) {
        UserInfo userInfo = new UserInfo();
        userInfo.setFullName(replaceHTMLTags(userInfoWrapper.getUserInfo().getFullName()));
        userInfo.setOrganization(replaceHTMLTags(userInfoWrapper.getUserInfo().getOrganization()));
        userInfo.setCountry(replaceHTMLTags(userInfoWrapper.getUserInfo().getCountry()));
        userInfo.setEmail(replaceHTMLTags(userInfoWrapper.getUserInfo().getEmail()));
        userInfo.setReceiveEmails(replaceHTMLTags(userInfoWrapper.getUserInfo().getReceiveEmails()));
        userInfo.setSubject(replaceHTMLTags(userInfoWrapper.getUserInfo().getSubject()));
        userInfo.setDescription(replaceHTMLTags(userInfoWrapper.getUserInfo().getDescription()));
        userInfo.setProductTitle(replaceHTMLTags(userInfoWrapper.getUserInfo().getProductTitle()));
        userInfo.setApprovalStatus(replaceHTMLTags(userInfoWrapper.getUserInfo().getApprovalStatus()));

        UserInfoWrapper cleanUserInfoWrapper = new UserInfoWrapper();
        cleanUserInfoWrapper.setUserInfo(userInfo);

        return cleanUserInfoWrapper;
    }

    private static String replaceHTMLTags(String input) {
        return escapeHtml4(input);
    }
}

package samplest;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import restx.admin.AdminModule;
import restx.factory.Module;
import restx.factory.Provides;
import restx.i18n.SupportedLocale;
import restx.security.*;
import samplest.security.CompanyAndSubCompanyRoles;
import samplest.foobar.Bar;

import javax.inject.Named;
import java.util.Locale;

/**
 * Date: 1/12/13
 * Time: 14:45
 */
@Module
public class AppModule {
    private ImmutableMap<String, RestxPrincipal> principals = ImmutableMap.<String, RestxPrincipal>builder()
            .put("admin", AdminModule.RESTX_ADMIN_PRINCIPAL)
            .put("user1", new StdUser("user1", ImmutableSet.<String>of("hello")))
            .put("user-belonging-to-1234-5678", new StdUser("user-belonging-to-1234-5678", ImmutableSet.<String>of(CompanyAndSubCompanyRoles.CAN_EDIT_COMPANY.getFor("1234", "5678"))))
            .put("user-managing-1234-subcompanies", new StdUser("user-managing-1234-subcompanies", ImmutableSet.<String>of(CompanyAndSubCompanyRoles.CAN_EDIT_COMPANY.getEverySubCompaniesForCompany("1234"))))
            .put("user-managing-all-companies", new StdUser("user-managing-companies", ImmutableSet.<String>of(CompanyAndSubCompanyRoles.CAN_EDIT_COMPANY.getForEveryCompanies())))
            .put("user-managing-all-parents-for-a-given-subcompany", new StdUser("user-managing-all-parents-for-a-given-subcompany", ImmutableSet.<String>of("CAN_EDIT_COMPANY_*_SUBCOMPANY_5678")))
            .build();

    @Provides @Named("restx.app.package")
    public String appPackage() {
        return "samplest";
    }

    @Provides
    public SupportedLocale french() {
        return new SupportedLocale(Locale.FRENCH);
    }

    @Provides
    public BasicPrincipalAuthenticator basicPrincipalAuthenticator(SecuritySettings securitySettings,
                                                                   final @Named("restx.admin.passwordHash") String adminPasswordHash) {
        return new StdBasicPrincipalAuthenticator(new UserService<RestxPrincipal>() {
            @Override
            public Optional<RestxPrincipal> findUserByName(String name) {
                return Optional.fromNullable(principals.get(name));
            }

            @Override
            public Optional<RestxPrincipal> findAndCheckCredentials(String name, String passwordHash) {
                RestxPrincipal principal = principals.get(name);
                if (principal == null || !adminPasswordHash.equals(passwordHash)) {
                    return Optional.absent();
                } else {
                    return Optional.of(principal);
                }
            }
        }, securitySettings);
    }

    @Provides @Named("mongo.db") String mongoDb() {
        return "testing-mongo";
    }

    @Provides
    public Bar buildBar() {
        return new Bar();
    }
}

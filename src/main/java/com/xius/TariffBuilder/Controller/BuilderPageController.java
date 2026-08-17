package com.xius.TariffBuilder.Controller;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

import com.xius.TariffBuilder.Dto.LoginRequestDto;
import com.xius.TariffBuilder.Dto.UsrPrivilegeDTO;
import com.xius.TariffBuilder.UserService.TariffService;
import com.xius.TariffBuilder.UserService.UserLoginService;

import jakarta.servlet.http.HttpSession;

/*
 * Holds only the endpoints that return view/page names (Thymeleaf/JSP templates
 * resolved via the view resolver). Split out of the original BuilderController
 * so page rendering and JSON APIs are cleanly separated.
 */
@Controller
@CrossOrigin(origins = "*")
public class BuilderPageController {

    private static final Logger logger = LoggerFactory.getLogger(BuilderPageController.class);

    private final UserLoginService userLoginService;

    private final TariffService tariffService;

    BuilderPageController(UserLoginService userLoginService, TariffService tariffService) {
        this.userLoginService = userLoginService;
        this.tariffService = tariffService;
    }

    // ================= LOGIN =================

    @GetMapping("/loginform")
    public String showLoginPage(HttpSession session, Model model) {

        model.addAttribute("sessionId", session.getId());

        if (!isNotLoggedIn(session)) {
            return "redirect:/builder";
        }

        model.addAttribute("loginForm", new LoginRequestDto());
        return "login";
    }

    @PostMapping("/login")
    public String login(@ModelAttribute("loginForm") LoginRequestDto request, HttpSession session, Model model) {

        logger.info("Login request received for user={}", request.getLoginId());

        if (request.getNetworkLoginName() == null || request.getNetworkLoginName().isBlank()) {

            logger.warn("Network name missing");

            model.addAttribute("message", "Please enter Network Name");

            return "login";
        }

        if (request.getLoginId() == null || request.getLoginId().isBlank()) {

            logger.warn("LoginId missing");

            model.addAttribute("message", "Please enter Username");

            return "login";
        }

        if (request.getPassword() == null || request.getPassword().isBlank()) {

            logger.warn("Password missing");

            model.addAttribute("message", "Please enter Password");

            return "login";
        }

        try {

            Map<String, Object> loginData = userLoginService.authenticate(request);

            List<UsrPrivilegeDTO> privileges = (List<UsrPrivilegeDTO>) loginData.get("privileges");

            List<String> privilegeIds = privileges.stream().map(UsrPrivilegeDTO::getPrivilegeId).distinct()
                    .collect(Collectors.toList());

            session.setAttribute("username", request.getLoginId());

            session.setAttribute("networkId", loginData.get("networkId"));

            session.setAttribute("privileges", privileges);

            session.setAttribute("privilegeIds", privilegeIds);

            logger.info("Login successful user={} networkId={} privilegesCount={}", request.getLoginId(),
                    loginData.get("networkId"), privileges.size());

            return "redirect:/builder";
        }

        catch (Exception ex) {

            logger.error("Login failed user={} error={}", request.getLoginId(), ex.getMessage(), ex);

            model.addAttribute("message", ex.getMessage());

            return "login";
        }
    }

    @GetMapping("/builder")
    public String builderHome(HttpSession session, Model model) {

        logger.info("Opening builder home");

        if (isNotLoggedIn(session)) {

            logger.warn("Unauthorized access to builder home");

            return "redirect:/loginform";
        }

        setCommonData(session, model);

        return "builder/step1";
    }

    // ================= ADMIN =================

    @GetMapping("/builder/admin")
    public String adminPage(HttpSession session, Model model) {

        logger.info("Opening admin page");

        if (isNotLoggedIn(session)) {

            logger.warn("Unauthorized admin page access");

            return "redirect:/loginform";
        }

        setCommonData(session, model);

        List<String> tariffList = tariffService.getTariffPackages();

        // log request info
        logger.info("Fetching TP list for admin user={} networkId={}",
                session.getAttribute("username"),
                session.getAttribute("networkId"));

        // log response data
        logger.info("TP list response size={}", tariffList.size());

        logger.debug("TP list data={}", tariffList);

        model.addAttribute("tariff", tariffList);

        return "builder/admin";
    }

    // ================= STEPS =================

    @GetMapping("/builder/step1")
    public String step1(HttpSession session, Model model) {

        logger.info("Opening step1");

        if (isNotLoggedIn(session)) {

            logger.warn("Unauthorized step1 access");

            return "redirect:/loginform";
        }

        setCommonData(session, model);

        return "builder/step1";
    }

    @GetMapping("/builder/step2")
    public String step2(HttpSession session, Model model) {

        logger.info("Opening step2");

        if (isNotLoggedIn(session)) {

            logger.warn("Unauthorized step2 access");

            return "redirect:/loginform";
        }

        setCommonData(session, model);

        return "builder/step2";
    }

    @GetMapping("/builder/step3")
    public String step3(HttpSession session, Model model) {

        logger.info("Opening step3");

        if (isNotLoggedIn(session)) {

            logger.warn("Unauthorized step3 access");

            return "redirect:/loginform";
        }

        setCommonData(session, model);

        return "builder/step3";
    }

    @GetMapping("/builder/step4")
    public String step4(HttpSession session, Model model) {

        logger.info("Opening step4");

        if (isNotLoggedIn(session)) {

            logger.warn("Unauthorized step4 access");

            return "redirect:/loginform";
        }

        setCommonData(session, model);

        return "builder/step4";
    }

    @GetMapping("/builder/step5")
    public String step5(HttpSession session, Model model) {

        logger.info("Opening step5");

        if (isNotLoggedIn(session)) {

            logger.warn("Unauthorized step5 access");

            return "redirect:/loginform";
        }

        setCommonData(session, model);

        return "builder/step5";
    }

    @GetMapping("/builder/step6")
    public String step6(HttpSession session, Model model) {

        logger.info("Opening step6");

        if (isNotLoggedIn(session)) {

            logger.warn("Unauthorized step6 access");

            return "redirect:/loginform";
        }

        setCommonData(session, model);

        return "builder/step6";
    }


    // ================= ATP CREATE =================
    //
    // Standalone page, same pattern as /builder/admin: one path, its own
    // template (atpcreate.html) with its own JS (Atpcreate.js) and CSS
    // (Atpcreate.css). The list (left pane) and
    // create/view panels (right pane) are all client-side panel-switches
    // within that one page — list comes from GET /builder/added-packages
    // (AtpRulesController), read-only view from GET /builder/atp-details
    // (AtpDetailsController, currently a 501 stub).
    @GetMapping("/builder/atpcreate")
    public String atpCreatePage(HttpSession session, Model model) {

        logger.info("Opening ATP Create page");

        if (isNotLoggedIn(session)) {

            logger.warn("Unauthorized ATP Create page access");

            return "redirect:/loginform";
        }

        setCommonData(session, model);

        return "builder/atpcreate";
    }


    @GetMapping("/logout")
    public String logout(HttpSession session) {

        logger.info("User logout username={}", session.getAttribute("username"));

        session.invalidate();

        return "redirect:/loginform";
    }

    // ================= COMMON =================

    private void setCommonData(HttpSession session, Model model) {

        logger.debug("Setting common session attributes");

        model.addAttribute("username", session.getAttribute("username"));

        model.addAttribute("networkId", session.getAttribute("networkId"));

        model.addAttribute("privileges", session.getAttribute("privileges"));

        model.addAttribute("privilegeIds", session.getAttribute("privilegeIds"));

        model.addAttribute("sessionId", session.getId());
    }

    private boolean isNotLoggedIn(HttpSession session) {

        boolean result = session.getAttribute("username") == null;

        if (result) {

            logger.debug("User not logged in");

        }

        return result;
    }
}
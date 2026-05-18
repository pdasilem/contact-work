package com.pdasilem.contactwork.ui;

import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.login.LoginForm;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

@Route("login")
@AnonymousAllowed
@CssImport("./styles/contactwork-app.css")
public class LoginView extends Composite<Div> implements BeforeEnterObserver {

    private final LoginForm loginForm = new LoginForm();

    public LoginView() {
        Div root = getContent();
        root.addClassName("cw-login-view");

        Div panel = new Div();
        panel.addClassName("cw-login-panel");

        H1 title = new H1("ContactWork");
        loginForm.setAction("login");

        panel.add(title, loginForm);
        root.add(panel);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (event.getLocation().getQueryParameters().getParameters().containsKey("error")) {
            loginForm.setError(true);
        }
    }
}

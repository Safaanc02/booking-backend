<#import "template.ftl" as layout>
<#--
  Pages d'information : page intermédiaire d'un lien d'invitation, confirmation
  après changement de mot de passe, accusés d'envoi d'e-mail.

  Seul gabarit surchargé du thème. Celui de `base` répète le message en titre
  puis en paragraphe, et propose l'action suivante sous forme de petit lien
  discret — or ces deux écrans sont les dernières marches de l'arrivée d'un
  gérant : c'est précisément là qu'il ne faut pas le perdre. Ici le message
  n'apparaît qu'une fois, et l'action suivante est un vrai bouton.
-->
<@layout.registrationLayout displayMessage=false; section>
    <#if section = "header">
        <#if messageHeader??>
            ${kcSanitize(msg("${messageHeader}"))?no_esc}
        <#else>
            ${message.summary}
        </#if>
    <#elseif section = "form">
        <div id="kc-info-message">
            <#-- Les actions restantes ne sont listées que s'il y en a : sans
                 elles, le paragraphe ne ferait que redire le titre. -->
            <#if requiredActions??>
                <p class="instruction">${msg("infoActionsRestantes")}</p>
                <ul class="b-etapes">
                    <#list requiredActions as reqActionItem>
                        <li>${kcSanitize(msg("requiredAction.${reqActionItem}"))?no_esc}</li>
                    </#list>
                </ul>
            </#if>

            <#if !skipLink??>
                <#assign suite = "">
                <#assign libelle = "">
                <#if pageRedirectUri?has_content>
                    <#assign suite = pageRedirectUri><#assign libelle = msg("backToApplication")>
                <#elseif actionUri?has_content>
                    <#assign suite = actionUri><#assign libelle = msg("proceedWithAction")>
                <#elseif (client.baseUrl)?has_content>
                    <#assign suite = client.baseUrl><#assign libelle = msg("backToApplication")>
                </#if>
                <#if suite?has_content>
                    <a id="kc-info-action"
                       class="${properties.kcButtonClass} ${properties.kcButtonPrimaryClass} ${properties.kcButtonBlockClass} ${properties.kcButtonLargeClass}"
                       href="${suite}">${kcSanitize(libelle)?no_esc}</a>
                </#if>
            </#if>
        </div>
    </#if>
</@layout.registrationLayout>

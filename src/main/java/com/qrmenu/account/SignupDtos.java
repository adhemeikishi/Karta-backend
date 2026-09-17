package com.qrmenu.account;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public class SignupDtos {

    private SignupDtos() {
    }

    /**
     * Pas de prénom/nom séparés : le projet n'a pas cette notion (voir compte ADMIN et
     * compte restaurateur historique, tous deux réduits à un identifiant de connexion). Le
     * nom du restaurant sert d'identifiant du travail créé — c'est lui qui apparaît partout
     * ensuite (navigation de l'Espace Restaurateur, QR, menu public).
     */
    public record SignupRequest(
            @NotBlank(message = "email is required")
            @Email(message = "email must be valid")
            @Size(max = 255)
            String email,

            @NotBlank(message = "password is required")
            @Size(min = 8, max = 255, message = "password must be at least 8 characters")
            String password,

            @NotBlank(message = "restaurantName is required")
            @Size(max = 255)
            String restaurantName
    ) {
    }

    /**
     * Le strict nécessaire pour que le frontend connecte immédiatement le compte qui vient
     * d'être créé : il connaît déjà l'email/mot de passe soumis, il lui manquait seulement
     * l'identifiant du restaurant pour savoir où naviguer (voir {@code AuthService.setCredentials}).
     */
    public record SignupResponse(UUID restaurantId) {
    }
}

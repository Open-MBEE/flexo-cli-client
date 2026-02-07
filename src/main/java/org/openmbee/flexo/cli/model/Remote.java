package org.openmbee.flexo.cli.model;

import java.util.Objects;

/**
 * Represents a remote MMS instance.
 * Similar to git remotes, allows working with multiple MMS servers.
 */
public class Remote {
    private String name;
    private String url;
    private String authEnabled;
    private String sshKeyPath;
    private String localMode;
    private String localUser;
    private String localJwtSecret;

    public Remote() {
    }

    public Remote(String name, String url) {
        this.name = name;
        this.url = url;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getAuthEnabled() {
        return authEnabled;
    }

    public void setAuthEnabled(String authEnabled) {
        this.authEnabled = authEnabled;
    }

    public String getSshKeyPath() {
        return sshKeyPath;
    }

    public void setSshKeyPath(String sshKeyPath) {
        this.sshKeyPath = sshKeyPath;
    }

    public String getLocalMode() {
        return localMode;
    }

    public void setLocalMode(String localMode) {
        this.localMode = localMode;
    }

    public String getLocalUser() {
        return localUser;
    }

    public void setLocalUser(String localUser) {
        this.localUser = localUser;
    }

    public String getLocalJwtSecret() {
        return localJwtSecret;
    }

    public void setLocalJwtSecret(String localJwtSecret) {
        this.localJwtSecret = localJwtSecret;
    }

    public boolean isAuthEnabledBoolean() {
        return authEnabled != null && Boolean.parseBoolean(authEnabled);
    }

    public boolean isLocalModeBoolean() {
        return localMode == null || Boolean.parseBoolean(localMode);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Remote remote = (Remote) o;
        return Objects.equals(name, remote.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public String toString() {
        return name + "\t" + url;
    }
}

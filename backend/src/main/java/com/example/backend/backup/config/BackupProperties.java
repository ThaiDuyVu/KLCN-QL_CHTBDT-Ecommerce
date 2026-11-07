package com.example.backend.backup.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * app.backup.*
 * <ul>
 *   <li>dir: where dump files are kept on the backend host.</li>
 *   <li>commandPrefix: blank = run pg_dump/pg_restore installed next to the backend; or e.g.
 *       {@code docker exec -i -e PGPASSWORD ecommerce-postgres} to use the database container's own tools
 *       (same major version as the server, recommended).</li>
 *   <li>host/port: where the tools reach PostgreSQL; blank = taken from spring.datasource.url
 *       (inside the container this is usually localhost:5432).</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "app.backup")
public class BackupProperties {
    private String dir = "./backups";
    private String commandPrefix = "";
    private String host = "";
    private Integer port;
    private Duration timeout = Duration.ofMinutes(10);

    public String getDir() { return dir; }
    public void setDir(String value) { this.dir = value; }
    public String getCommandPrefix() { return commandPrefix; }
    public void setCommandPrefix(String value) { this.commandPrefix = value == null ? "" : value; }
    public String getHost() { return host; }
    public void setHost(String value) { this.host = value == null ? "" : value; }
    public Integer getPort() { return port; }
    public void setPort(Integer value) { this.port = value; }
    public Duration getTimeout() { return timeout; }
    public void setTimeout(Duration value) { this.timeout = value; }
}

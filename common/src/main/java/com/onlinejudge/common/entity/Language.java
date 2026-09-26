package com.onlinejudge.common.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A judgeable language runtime (PRD §13). {@code compileCmd} is null for interpreted
 * languages, which skip the COMPILING stage entirely.
 */
@Entity
@Table(name = "languages")
public class Language {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String name;

    @Column(name = "source_filename", nullable = false, length = 100)
    private String sourceFilename;

    /** Null for interpreted languages. */
    @Column(name = "compile_cmd")
    private String compileCmd;

    @Column(name = "run_cmd", nullable = false)
    private String runCmd;

    @Column(name = "docker_image", nullable = false, length = 200)
    private String dockerImage;

    @Column(name = "time_limit_multiplier", nullable = false, precision = 3, scale = 2)
    private BigDecimal timeLimitMultiplier = BigDecimal.ONE;

    @Column(name = "is_enabled", nullable = false)
    private boolean enabled = true;

    protected Language() {
    }

    public Language(String name, String sourceFilename, String compileCmd, String runCmd,
                    String dockerImage, BigDecimal timeLimitMultiplier) {
        this.name = name;
        this.sourceFilename = sourceFilename;
        this.compileCmd = compileCmd;
        this.runCmd = runCmd;
        this.dockerImage = dockerImage;
        this.timeLimitMultiplier = timeLimitMultiplier;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getSourceFilename() {
        return sourceFilename;
    }

    public boolean isCompiled() {
        return compileCmd != null;
    }

    public String getCompileCmd() {
        return compileCmd;
    }

    public String getRunCmd() {
        return runCmd;
    }

    public String getDockerImage() {
        return dockerImage;
    }

    public BigDecimal getTimeLimitMultiplier() {
        return timeLimitMultiplier;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}

-- URL Shortener Database Schema v2.0
-- PostgreSQL with AI-Powered Features

-- URL Mapping Table (Enhanced)
CREATE TABLE IF NOT EXISTS url_mapping (
    id BIGSERIAL PRIMARY KEY,
    original_url TEXT NOT NULL,
    short_key VARCHAR(20) UNIQUE,
    custom_alias VARCHAR(50) UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NULL,
    click_count BIGINT NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT true,
    
    -- Password Protection
    password_hash VARCHAR(255) NULL,
    is_password_protected BOOLEAN NOT NULL DEFAULT false,
    
    -- AI-Generated Metadata
    ai_summary TEXT NULL,
    ai_category VARCHAR(100) NULL,
    ai_tags TEXT NULL,
    ai_safety_score DECIMAL(3,2) NULL,
    ai_analyzed_at TIMESTAMP NULL,
    
    -- URL Metadata (Open Graph)
    meta_title VARCHAR(500) NULL,
    meta_description TEXT NULL,
    meta_image_url TEXT NULL,
    meta_favicon_url TEXT NULL,
    meta_fetched_at TIMESTAMP NULL,
    
    -- Creator Info
    created_by_ip VARCHAR(45) NULL,
    user_agent TEXT NULL
);

-- Click Analytics Table
CREATE TABLE IF NOT EXISTS click_analytics (
    id BIGSERIAL PRIMARY KEY,
    url_mapping_id BIGINT NOT NULL REFERENCES url_mapping(id) ON DELETE CASCADE,
    clicked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Client Info
    client_ip VARCHAR(45) NULL,
    user_agent TEXT NULL,
    referer TEXT NULL,
    
    -- Parsed User Agent
    browser_name VARCHAR(100) NULL,
    browser_version VARCHAR(50) NULL,
    os_name VARCHAR(100) NULL,
    os_version VARCHAR(50) NULL,
    device_type VARCHAR(50) NULL,
    
    -- Geolocation (from IP)
    country_code VARCHAR(2) NULL,
    country_name VARCHAR(100) NULL,
    city VARCHAR(100) NULL,
    region VARCHAR(100) NULL,
    latitude DECIMAL(10,8) NULL,
    longitude DECIMAL(11,8) NULL,
    timezone VARCHAR(100) NULL
);

-- AI Analysis Cache Table
CREATE TABLE IF NOT EXISTS ai_analysis_cache (
    id BIGSERIAL PRIMARY KEY,
    url_hash VARCHAR(64) NOT NULL UNIQUE,
    original_url TEXT NOT NULL,
    summary TEXT NULL,
    category VARCHAR(100) NULL,
    tags TEXT NULL,
    safety_score DECIMAL(3,2) NULL,
    is_safe BOOLEAN DEFAULT true,
    safety_reasons TEXT NULL,
    analyzed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL
);

-- URL Groups/Collections Table
CREATE TABLE IF NOT EXISTS url_group (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_public BOOLEAN NOT NULL DEFAULT false
);

-- URL Group Membership
CREATE TABLE IF NOT EXISTS url_group_membership (
    id BIGSERIAL PRIMARY KEY,
    url_mapping_id BIGINT NOT NULL REFERENCES url_mapping(id) ON DELETE CASCADE,
    group_id BIGINT NOT NULL REFERENCES url_group(id) ON DELETE CASCADE,
    added_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(url_mapping_id, group_id)
);

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_short_key ON url_mapping(short_key);
CREATE INDEX IF NOT EXISTS idx_custom_alias ON url_mapping(custom_alias);
CREATE INDEX IF NOT EXISTS idx_expires_at ON url_mapping(expires_at) WHERE expires_at IS NOT NULL AND is_active = true;
CREATE INDEX IF NOT EXISTS idx_is_active ON url_mapping(is_active);
CREATE INDEX IF NOT EXISTS idx_ai_category ON url_mapping(ai_category);
CREATE INDEX IF NOT EXISTS idx_created_at ON url_mapping(created_at);

CREATE INDEX IF NOT EXISTS idx_click_url_mapping ON click_analytics(url_mapping_id);
CREATE INDEX IF NOT EXISTS idx_click_timestamp ON click_analytics(clicked_at);
CREATE INDEX IF NOT EXISTS idx_click_country ON click_analytics(country_code);
CREATE INDEX IF NOT EXISTS idx_click_device ON click_analytics(device_type);

CREATE INDEX IF NOT EXISTS idx_ai_cache_hash ON ai_analysis_cache(url_hash);
CREATE INDEX IF NOT EXISTS idx_ai_cache_expires ON ai_analysis_cache(expires_at);

-- Comments
COMMENT ON TABLE url_mapping IS 'Core URL mappings with AI-powered metadata';
COMMENT ON TABLE click_analytics IS 'Detailed click analytics with geo and device info';
COMMENT ON TABLE ai_analysis_cache IS 'Cached AI analysis results to avoid redundant API calls';
COMMENT ON TABLE url_group IS 'Collections for organizing URLs';

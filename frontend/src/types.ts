export interface UrlMetadata {
    title?: string;
    description?: string;
    imageUrl?: string;
    faviconUrl?: string;
    siteName?: string;
    type?: string;
    author?: string;
    keywords?: string;
    canonicalUrl?: string;
    textContent?: string;
}

export interface AiAnalysisResult {
    summary?: string;
    category?: string;
    tags?: string[];
    safetyScore?: number;
    isSafe?: boolean;
    safetyReasons?: string[];
    aliasSuggestions?: string[];
    fromCache?: boolean;
}

export interface CreateUrlRequest {
    originalUrl: string;
    expiresAt?: string; // ISO 8601
    customAlias?: string;
    password?: string;
    fetchMetadata?: boolean;
    enableAiAnalysis?: boolean;
    generateQrCode?: boolean;
}

export interface CreateUrlResponse {
    shortUrl: string;
    shortKey: string;
    customAlias?: string;
    isPasswordProtected?: boolean;
    qrCode?: string; // Base64
    metadata?: UrlMetadata;
    aiAnalysis?: AiAnalysisResult;
}

export interface UrlStatsResponse {
    originalUrl: string;
    clickCount: number;
    createdAt: string;
    expiresAt?: string;
}

export interface DetailedAnalytics {
    shortKey: string;
    originalUrl: string;
    totalClicks: number;
    createdAt: string;
    expiresAt?: string;
    
    // Maps
    clicksByCountry: Record<string, number>;
    clicksByDevice: Record<string, number>;
    clicksByBrowser: Record<string, number>;
    clicksByOs: Record<string, number>;
    clicksByReferer: Record<string, number>;
    clicksOverTime: Record<string, number>; // date -> count
    
    // AI & Meta
    aiSummary?: string;
    aiCategory?: string;
    aiTags?: string; // Comma separated string from backend
    
    metaTitle?: string;
    metaDescription?: string;
    metaImageUrl?: string;
}

export interface ErrorResponse {
    status: number;
    error: string;
    message: string;
    path: string;
    timestamp: string;
}

export interface BulkCreateRequest {
    urls: CreateUrlRequest[];
    fetchMetadata?: boolean;
    enableAiAnalysis?: boolean;
}

export interface BulkCreateResponse {
    successCount: number;
    failedCount: number;
    results: CreateUrlResponse[];
    errors: {
        index: number;
        originalUrl: string;
        error: string;
    }[];
}

export interface UrlListItem {
    shortKey: string;
    customAlias?: string;
    effectiveKey: string;
    originalUrl: string;
    shortUrl: string;
    clickCount: number;
    createdAt: string;
    expiresAt?: string;
    isPasswordProtected: boolean;
    metaTitle?: string;
    aiCategory?: string;
}


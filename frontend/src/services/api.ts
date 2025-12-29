import axios from 'axios';
import type { CreateUrlRequest, CreateUrlResponse, DetailedAnalytics, UrlStatsResponse, UrlListItem, BulkCreateRequest, BulkCreateResponse } from '../types';

const API_BASE_URL = '/api';

const api = axios.create({
    baseURL: API_BASE_URL,
    headers: {
        'Content-Type': 'application/json',
    },
});

// Request interceptor to handle FormData - remove Content-Type header for FormData
api.interceptors.request.use((config) => {
    if (config.data instanceof FormData) {
        // Remove Content-Type header to let browser set it with boundary
        delete config.headers['Content-Type'];
    }
    return config;
});

export const urlService = {
    getAllUrls: async (): Promise<UrlListItem[]> => {
        const response = await api.get<UrlListItem[]>('/urls');
        return response.data;
    },

    createShortUrl: async (data: CreateUrlRequest): Promise<CreateUrlResponse> => {
        const response = await api.post<CreateUrlResponse>('/urls', data);
        return response.data;
    },

    createBulkShortUrls: async (data: BulkCreateRequest): Promise<BulkCreateResponse> => {
        const response = await api.post<BulkCreateResponse>('/urls/bulk', data);
        return response.data;
    },

    createBulkShortUrlsFromCsv: async (
        file: File,
        fetchMetadata: boolean = false,
        enableAiAnalysis: boolean = false
    ): Promise<BulkCreateResponse> => {
        const formData = new FormData();
        formData.append('file', file);
        formData.append('fetchMetadata', String(fetchMetadata));
        formData.append('enableAiAnalysis', String(enableAiAnalysis));
        
        // Don't set Content-Type header - let axios set it automatically with boundary
        const response = await api.post<BulkCreateResponse>('/urls/bulk/csv', formData);
        return response.data;
    },

    getStats: async (shortKey: string): Promise<UrlStatsResponse> => {
        // Encode the shortKey to handle special characters
        const encodedKey = encodeURIComponent(shortKey);
        const response = await api.get<UrlStatsResponse>(`/urls/${encodedKey}/stats`);
        return response.data;
    },

    getAnalytics: async (shortKey: string): Promise<DetailedAnalytics> => {
        // Encode the shortKey to handle special characters
        const encodedKey = encodeURIComponent(shortKey);
        const response = await api.get<DetailedAnalytics>(`/urls/${encodedKey}/analytics`);
        return response.data;
    },

    checkProtection: async (shortKey: string): Promise<{ passwordRequired: boolean }> => {
        // Encode the shortKey to handle special characters
        const encodedKey = encodeURIComponent(shortKey);
        const response = await api.get<{ passwordRequired: boolean }>(`/urls/${encodedKey}/protected`);
        return response.data;
    },

    unlockUrl: async (shortKey: string, password: string): Promise<string> => {
        // Note: This endpoint is at root level, not /api
        // We need to use a different base URL or full URL
        const encodedKey = encodeURIComponent(shortKey);
        const response = await fetch(`/${encodedKey}?password=${encodeURIComponent(password)}`, {
            method: 'GET',
            redirect: 'manual' // Don't follow redirect automatically
        });
        
        if (response.status === 302) {
            const location = response.headers.get('Location');
            if (location) {
                return location;
            }
        } else if (response.status === 401) {
            throw new Error('Invalid password');
        } else if (response.status === 404) {
            throw new Error('URL not found');
        }
        throw new Error('Failed to unlock URL');
    }
};

export const aiService = {
    analyzeUrl: async (url: string) => {
        const response = await api.post('/ai/analyze', null, { params: { url } });
        return response.data;
    },
    
    checkSafety: async (url: string) => {
        const response = await api.post('/ai/safety-check', null, { params: { url } });
        return response.data;
    }
};

export default api;


import axios from 'axios';
import type { CreateUrlRequest, CreateUrlResponse, DetailedAnalytics, UrlStatsResponse, UrlListItem } from '../types';

const API_BASE_URL = '/api';

const api = axios.create({
    baseURL: API_BASE_URL,
    headers: {
        'Content-Type': 'application/json',
    },
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


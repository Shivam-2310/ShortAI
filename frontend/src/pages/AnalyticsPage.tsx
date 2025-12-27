import React, { useState, useEffect } from 'react';
import { useSearchParams } from 'react-router-dom';
import { urlService } from '../services/api';
import type { DetailedAnalytics, UrlListItem } from '../types';
import { AnalyticsDashboard } from '../components/AnalyticsDashboard';
import { Search, Loader2, Link2, Lock, Calendar, MousePointer } from 'lucide-react';
import toast from 'react-hot-toast';

export const AnalyticsPage: React.FC = () => {
    const [searchParams, setSearchParams] = useSearchParams();
    const urlKey = searchParams.get('key') || '';
    
    const [searchQuery, setSearchQuery] = useState(''); // Separate search query from selected URL
    const [loading, setLoading] = useState(false);
    const [data, setData] = useState<DetailedAnalytics | null>(null);
    const [urls, setUrls] = useState<UrlListItem[]>([]);
    const [loadingUrls, setLoadingUrls] = useState(true);

    // Extract short key from URL if full URL is provided
    const extractShortKey = (input: string): string => {
        if (!input) return '';
        
        const trimmed = input.trim();
        if (!trimmed) return '';
        
        // If it's a full URL (starts with http:// or https://), extract the key part
        if (trimmed.startsWith('http://') || trimmed.startsWith('https://')) {
            try {
                const url = new URL(trimmed);
                // Get the pathname and remove leading slash
                const path = url.pathname.replace(/^\//, '').replace(/\/$/, '');
                // Remove any query params or fragments that might be in the path
                return path.split('?')[0].split('#')[0];
            } catch {
                // If URL parsing fails, try to extract manually
                // Remove protocol and domain, get the path
                const match = trimmed.match(/https?:\/\/[^\/]+\/(.+)/);
                if (match && match[1]) {
                    return match[1].split('?')[0].split('#')[0];
                }
            }
        }
        
        // If it contains slashes but no protocol, might be a relative URL
        if (trimmed.includes('/')) {
            // Get the last part after the last slash
            const parts = trimmed.split('/').filter(p => p);
            return parts[parts.length - 1].split('?')[0].split('#')[0];
        }
        
        // Assume it's already a key, just clean it up
        return trimmed.split('?')[0].split('#')[0];
    };

    const fetchAnalytics = async (key: string) => {
        if (!key) return;
        
        // Extract just the key part if a full URL was provided
        const shortKey = extractShortKey(key);
        if (!shortKey) {
            toast.error('Please enter a valid short key');
            return;
        }
        
        setLoading(true);
        try {
            const result = await urlService.getAnalytics(shortKey);
            setData(result);
        } catch (error: any) {
            console.error(error);
            const errorMsg = error.response?.data?.message || 'Could not fetch analytics. Check the key.';
            toast.error(errorMsg);
            setData(null);
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        // Fetch all URLs on mount
        const loadUrls = async () => {
            try {
                setLoadingUrls(true);
                const allUrls = await urlService.getAllUrls();
                setUrls(allUrls);
                
                // Auto-select first URL if no key is in URL params and we have URLs
                if (!urlKey && allUrls.length > 0) {
                    const firstUrl = allUrls[0];
                    setSearchParams({ key: firstUrl.effectiveKey });
                }
            } catch (error: any) {
                console.error('Failed to load URLs:', error);
                toast.error('Failed to load URL list');
            } finally {
                setLoadingUrls(false);
            }
        };
        loadUrls();
    }, []);

    useEffect(() => {
        if (urlKey) {
            const shortKey = extractShortKey(urlKey);
            if (shortKey) {
                fetchAnalytics(shortKey);
            }
        } else if (urls.length > 0 && !data) {
            // If no key but we have URLs, select the first one
            const firstUrl = urls[0];
            setSearchParams({ key: firstUrl.effectiveKey });
        }
    }, [urlKey, urls]);

    const handleUrlClick = (item: UrlListItem) => {
        // Don't update search query, just select the URL
        setSearchParams({ key: item.effectiveKey });
    };

    const handleSearch = (e: React.FormEvent) => {
        e.preventDefault();
        // Search doesn't change the selected URL, it just filters the list
    };

    return (
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
            {/* Left Sidebar - URL List */}
            <div className="lg:col-span-1 space-y-4">
                <div className="bg-white p-6 rounded-2xl shadow-sm border border-gray-100">
                    <h2 className="text-xl font-bold text-gray-900 mb-4">All Shortened Links</h2>
                    
                    {/* Search Form */}
                    <form onSubmit={handleSearch} className="mb-4">
                        <div className="relative">
                            <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 w-4 h-4" />
                            <input
                                type="text"
                                placeholder="Search by key..."
                                className="w-full pl-10 pr-4 py-2 border border-gray-200 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-transparent outline-none text-sm"
                                value={searchQuery}
                                onChange={(e) => setSearchQuery(e.target.value)}
                            />
                        </div>
                    </form>

                    {/* URL List */}
                    <div className="space-y-2 max-h-[calc(100vh-300px)] overflow-y-auto">
                        {loadingUrls ? (
                            <div className="flex items-center justify-center py-8">
                                <Loader2 className="animate-spin w-6 h-6 text-indigo-600" />
                            </div>
                        ) : urls.length === 0 ? (
                            <div className="text-center py-8 text-gray-500 text-sm">
                                No URLs found
                            </div>
                        ) : (
                            urls
                                .filter(item => 
                                    !searchQuery || 
                                    item.effectiveKey.toLowerCase().includes(searchQuery.toLowerCase()) ||
                                    item.originalUrl.toLowerCase().includes(searchQuery.toLowerCase()) ||
                                    (item.metaTitle && item.metaTitle.toLowerCase().includes(searchQuery.toLowerCase()))
                                )
                                .map((item) => (
                                    <div
                                        key={item.effectiveKey}
                                        onClick={() => handleUrlClick(item)}
                                        className={`p-4 rounded-lg border cursor-pointer transition-all hover:shadow-md ${
                                            urlKey === item.effectiveKey
                                                ? 'border-indigo-500 bg-indigo-50'
                                                : 'border-gray-200 hover:border-indigo-300'
                                        }`}
                                    >
                                        <div className="flex items-start justify-between mb-2">
                                            <div className="flex items-center gap-2 flex-1 min-w-0">
                                                <Link2 className="w-4 h-4 text-indigo-600 flex-shrink-0" />
                                                <span className="font-medium text-sm text-gray-900 truncate">
                                                    {item.effectiveKey}
                                                </span>
                                                {item.isPasswordProtected && (
                                                    <Lock className="w-3 h-3 text-gray-400 flex-shrink-0" />
                                                )}
                                            </div>
                                        </div>
                                        
                                        <p className="text-xs text-gray-500 truncate mb-2" title={item.originalUrl}>
                                            {item.metaTitle || item.originalUrl}
                                        </p>
                                        
                                        <div className="flex items-center gap-4 text-xs text-gray-400">
                                            <div className="flex items-center gap-1">
                                                <MousePointer className="w-3 h-3" />
                                                <span>{item.clickCount}</span>
                                            </div>
                                            <div className="flex items-center gap-1">
                                                <Calendar className="w-3 h-3" />
                                                <span>{new Date(item.createdAt).toLocaleDateString()}</span>
                                            </div>
                                        </div>
                                        
                                        {item.aiCategory && (
                                            <div className="mt-2">
                                                <span className="inline-block px-2 py-0.5 bg-purple-100 text-purple-700 text-xs rounded">
                                                    {item.aiCategory}
                                                </span>
                                            </div>
                                        )}
                                    </div>
                                ))
                        )}
                    </div>
                </div>
            </div>

            {/* Right Side - Analytics Dashboard */}
            <div className="lg:col-span-2 space-y-6">
                {data ? (
                    <AnalyticsDashboard data={data} />
                ) : (
                    !loading && (
                        <div className="text-center py-20 bg-white rounded-2xl border border-dashed border-gray-200">
                            <div className="bg-gray-50 w-16 h-16 rounded-full flex items-center justify-center mx-auto mb-4">
                                <Search className="w-8 h-8 text-gray-400" />
                            </div>
                            <h3 className="text-lg font-medium text-gray-900">Select a URL to view analytics</h3>
                            <p className="text-gray-500 mt-1">Click on any shortened link from the list to see its detailed statistics.</p>
                        </div>
                    )
                )}
            </div>
        </div>
    );
};


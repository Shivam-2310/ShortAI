import React from 'react';
import type { CreateUrlResponse } from '../types';
import { Copy, ExternalLink, ShieldCheck, ShieldAlert, Tag, BarChart2 } from 'lucide-react';
import toast from 'react-hot-toast';
import { Link } from 'react-router-dom';

interface ResultCardProps {
    data: CreateUrlResponse;
}

export const ResultCard: React.FC<ResultCardProps> = ({ data }) => {
    const copyToClipboard = () => {
        navigator.clipboard.writeText(data.shortUrl);
        toast.success('Copied to clipboard!');
    };

    const isSafe = data.aiAnalysis?.isSafe !== false;

    return (
        <div className="w-full max-w-2xl mx-auto mt-8 bg-white rounded-2xl shadow-lg border border-gray-100 overflow-hidden animate-fade-in-up">
            <div className="bg-indigo-50 p-6 border-b border-indigo-100 flex items-center justify-between">
                <div>
                    <h3 className="text-lg font-semibold text-indigo-900">Short URL Created!</h3>
                    <p className="text-indigo-600 text-sm mt-1">{data.metadata?.title || 'No title detected'}</p>
                </div>
                <div className="flex gap-2">
                    <Link to={`/analytics?key=${data.shortKey}`} className="p-2 bg-white text-indigo-600 rounded-lg hover:bg-indigo-50 transition-colors border border-indigo-200" title="View Analytics">
                        <BarChart2 className="w-5 h-5" />
                    </Link>
                </div>
            </div>

            <div className="p-6 space-y-6">
                {/* Short Link Display */}
                <div className="flex items-center space-x-2 bg-gray-50 p-4 rounded-xl border border-gray-200">
                    <input
                        type="text"
                        readOnly
                        value={data.shortUrl}
                        className="flex-1 bg-transparent border-none focus:ring-0 text-gray-700 font-medium text-lg"
                    />
                    <button
                        onClick={copyToClipboard}
                        className="p-2 text-gray-500 hover:text-indigo-600 hover:bg-white rounded-lg transition-all"
                        title="Copy"
                    >
                        <Copy className="w-5 h-5" />
                    </button>
                    <a
                        href={data.shortUrl}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="p-2 text-gray-500 hover:text-indigo-600 hover:bg-white rounded-lg transition-all"
                        title="Open"
                    >
                        <ExternalLink className="w-5 h-5" />
                    </a>
                </div>

                <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                    {/* QR Code */}
                    {data.qrCode && (
                        <div className="flex flex-col items-center justify-center p-4 bg-gray-50 rounded-xl border border-gray-200">
                            <span className="text-sm font-medium text-gray-500 mb-3">QR Code</span>
                            <img
                                src={`data:image/png;base64,${data.qrCode}`}
                                alt="QR Code"
                                className="w-32 h-32"
                            />
                        </div>
                    )}

                    {/* AI Analysis Summary */}
                    <div className="space-y-4">
                        {data.aiAnalysis ? (
                            <>
                                <div>
                                    <div className="flex items-center justify-between mb-2">
                                        <span className="text-sm font-medium text-gray-500 flex items-center">
                                            <ShieldCheck className={`w-4 h-4 mr-1 ${isSafe ? 'text-green-600' : 'text-red-600'}`} />
                                            AI Safety Score
                                        </span>
                                        <span className={`flex items-center text-sm font-bold ${isSafe ? 'text-green-600' : 'text-red-600'}`}>
                                            {isSafe ? <ShieldCheck className="w-4 h-4 mr-1" /> : <ShieldAlert className="w-4 h-4 mr-1" />}
                                            {data.aiAnalysis.safetyScore ? `${(data.aiAnalysis.safetyScore * 100).toFixed(0)}%` : 'N/A'}
                                        </span>
                                    </div>
                                    {data.aiAnalysis.safetyScore && (
                                        <div className="w-full bg-gray-200 rounded-full h-2.5">
                                            <div
                                                className={`h-2.5 rounded-full transition-all duration-500 ${isSafe ? 'bg-green-500' : 'bg-red-500'}`}
                                                style={{ width: `${(data.aiAnalysis.safetyScore * 100)}%` }}
                                            />
                                        </div>
                                    )}
                                    {data.aiAnalysis.safetyReasons && data.aiAnalysis.safetyReasons.length > 0 && (
                                        <div className="mt-2 text-xs text-gray-500">
                                            {data.aiAnalysis.safetyReasons.map((reason, i) => (
                                                <div key={i} className="flex items-start">
                                                    <span className="mr-1">•</span>
                                                    <span>{reason}</span>
                                                </div>
                                            ))}
                                        </div>
                                    )}
                                </div>

                                {data.aiAnalysis.category && (
                                    <div>
                                        <span className="text-sm font-medium text-gray-500 block mb-1">Category</span>
                                        <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-indigo-100 text-indigo-800">
                                            {data.aiAnalysis.category}
                                        </span>
                                    </div>
                                )}

                                {data.aiAnalysis.tags && data.aiAnalysis.tags.length > 0 && (
                                    <div>
                                        <span className="text-sm font-medium text-gray-500 block mb-1">Tags</span>
                                        <div className="flex flex-wrap gap-1">
                                            {data.aiAnalysis.tags.map((tag, i) => (
                                                <span key={i} className="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-gray-100 text-gray-800">
                                                    <Tag className="w-3 h-3 mr-1" /> {tag}
                                                </span>
                                            ))}
                                        </div>
                                    </div>
                                )}

                                {data.aiAnalysis.aliasSuggestions && data.aiAnalysis.aliasSuggestions.length > 0 && (
                                    <div>
                                        <span className="text-sm font-medium text-gray-500 block mb-1">Suggested Aliases</span>
                                        <div className="flex flex-wrap gap-1">
                                            {data.aiAnalysis.aliasSuggestions.map((alias, i) => (
                                                <span key={i} className="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-purple-100 text-purple-800">
                                                    {alias}
                                                </span>
                                            ))}
                                        </div>
                                    </div>
                                )}
                            </>
                        ) : (
                            <div className="flex flex-col items-center justify-center h-full text-gray-400 text-sm py-4">
                                <BrainIcon className="w-8 h-8 mb-2 opacity-50" />
                                <span>AI Analysis not available</span>
                                <span className="text-xs mt-1">Analysis may be processing in background</span>
                            </div>
                        )}
                    </div>
                </div>

                {/* Summary Text */}
                {data.aiAnalysis?.summary && (
                    <div className="bg-gradient-to-r from-indigo-50 to-purple-50 p-5 rounded-xl border border-indigo-100">
                        <h4 className="text-sm font-semibold text-indigo-900 mb-2 flex items-center">
                            <BrainIcon className="w-4 h-4 mr-2 text-indigo-600" />
                            AI Summary
                            {data.aiAnalysis.fromCache && (
                                <span className="ml-2 text-xs text-gray-500">(Cached)</span>
                            )}
                        </h4>
                        <p className="text-sm text-gray-700 leading-relaxed">
                            {data.aiAnalysis.summary}
                        </p>
                    </div>
                )}
            </div>
        </div>
    );
};

// Helper icon since lucide-react Brain might conflict if imported twice or just use Brain from lucide
import { Brain as BrainIcon } from 'lucide-react';


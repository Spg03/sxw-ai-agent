type BrandLogoProps = {
  compact?: boolean
  className?: string
}

/** AgentForge 的统一品牌标识。 */
export default function BrandLogo({ compact = false, className = '' }: BrandLogoProps) {
  return <div className={`brand-logo ${compact ? 'brand-logo-compact' : ''} ${className}`.trim()} aria-label="AgentForge">
    <svg className="brand-logo-mark" viewBox="0 0 36 36" role="img" aria-hidden="true">
      <defs>
        <linearGradient id="agentforge-a-gradient" x1="5" y1="29" x2="29" y2="5" gradientUnits="userSpaceOnUse">
          <stop stopColor="#7768f6" />
          <stop offset=".52" stopColor="#7867f2" />
          <stop offset="1" stopColor="#39a8f8" />
        </linearGradient>
      </defs>
      <path fill="url(#agentforge-a-gradient)" d="M18.2 4.7c1 0 1.9.58 2.32 1.48l9.83 20.95a2.57 2.57 0 0 1-4.65 2.2l-2.13-4.53H12.5l-2.1 4.52a2.57 2.57 0 1 1-4.66-2.17L15.87 6.2a2.57 2.57 0 0 1 2.33-1.5Zm0 8.59-3.3 7.06h6.62l-3.32-7.06Z" />
      <path fill="#d8e3ff" opacity=".92" d="m18.18 13.3 2.17 4.63h-4.34l2.17-4.63Z" />
    </svg>
    {!compact && <span className="brand-logo-name">AgentForge</span>}
  </div>
}

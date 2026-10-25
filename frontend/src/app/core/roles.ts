export type Role = 'ADMIN' | 'DISPATCHER' | 'VIEWER';

export interface Identity {
  username: string;
  tenant: string | null;
  roles: Role[];
}

const KNOWN: Role[] = ['ADMIN', 'DISPATCHER', 'VIEWER'];

/**
 * Reads who the user is from the access token's claims. Only for showing or hiding buttons:
 * the services make the real decisions from the verified token.
 */
export function identityFromClaims(claims: Record<string, unknown> | null): Identity | null {
  if (!claims) {
    return null;
  }
  const direct = Array.isArray(claims['roles']) ? (claims['roles'] as unknown[]) : [];
  const realm = (claims['realm_access'] as { roles?: unknown[] } | undefined)?.roles ?? [];
  const roles = [...direct, ...realm].filter((r): r is Role => KNOWN.includes(r as Role));
  return {
    username: String(claims['preferred_username'] ?? claims['sub'] ?? 'unknown'),
    tenant: typeof claims['tenant_id'] === 'string' ? (claims['tenant_id'] as string) : null,
    roles: [...new Set(roles)],
  };
}

export function decodeJwtPayload(token: string | null): Record<string, unknown> | null {
  if (!token) {
    return null;
  }
  const part = token.split('.')[1];
  if (!part) {
    return null;
  }
  try {
    const json = atob(part.replace(/-/g, '+').replace(/_/g, '/'));
    return JSON.parse(decodeURIComponent(escape(json)));
  } catch {
    return null;
  }
}

export function canDispatch(id: Identity | null): boolean {
  return !!id && (id.roles.includes('DISPATCHER') || id.roles.includes('ADMIN'));
}

export function isAdmin(id: Identity | null): boolean {
  return !!id && id.roles.includes('ADMIN');
}

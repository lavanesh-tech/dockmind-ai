import { describe, expect, it } from 'vitest';
import { canDispatch, decodeJwtPayload, identityFromClaims, isAdmin } from './roles';

function fakeJwt(claims: object): string {
  const b64 = (o: object) => btoa(JSON.stringify(o)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  return `${b64({ alg: 'none' })}.${b64(claims)}.sig`;
}

describe('identity from token claims', () => {
  it('reads Keycloak realm roles, tenant and username', () => {
    const id = identityFromClaims(decodeJwtPayload(fakeJwt({
      preferred_username: 'dispatcher.acme', tenant_id: 'acme',
      realm_access: { roles: ['DISPATCHER', 'offline_access', 'default-roles-dockmind'] },
    })));
    expect(id).toEqual({ username: 'dispatcher.acme', tenant: 'acme', roles: ['DISPATCHER'] });
    expect(canDispatch(id)).toBe(true);
    expect(isAdmin(id)).toBe(false);
  });

  it('accepts a top-level roles claim and ignores unknown roles', () => {
    const id = identityFromClaims({ sub: 'u1', roles: ['ADMIN', 'SUPERUSER'] });
    expect(id?.roles).toEqual(['ADMIN']);
    expect(id?.tenant).toBeNull();
    expect(canDispatch(id)).toBe(true);
  });

  it('a viewer can neither dispatch nor administer', () => {
    const id = identityFromClaims({ sub: 'v', realm_access: { roles: ['VIEWER'] } });
    expect(canDispatch(id)).toBe(false);
    expect(isAdmin(id)).toBe(false);
  });

  it('treats missing or malformed tokens as logged out', () => {
    expect(decodeJwtPayload(null)).toBeNull();
    expect(decodeJwtPayload('not-a-jwt')).toBeNull();
    expect(decodeJwtPayload('a.%%%.c')).toBeNull();
    expect(identityFromClaims(null)).toBeNull();
  });
});

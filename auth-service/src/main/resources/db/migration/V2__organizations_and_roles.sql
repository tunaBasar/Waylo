-- Additive only: existing users, credentials and global USER/ADMIN roles are unchanged.
CREATE TABLE organizations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL CHECK (length(btrim(name)) > 0),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'ACTIVE', 'SUSPENDED', 'ARCHIVED')),
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE organization_memberships (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'SUSPENDED', 'REVOKED')),
    permission_version BIGINT NOT NULL DEFAULT 0 CHECK (permission_version >= 0),
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_membership_user_organization UNIQUE (user_id, organization_id),
    CONSTRAINT uq_membership_id_organization UNIQUE (id, organization_id)
);

CREATE INDEX idx_memberships_organization_status ON organization_memberships(organization_id, status);

CREATE TABLE role_assignments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    membership_id UUID NOT NULL,
    organization_id UUID NOT NULL,
    role VARCHAR(40) NOT NULL
        CHECK (role IN ('ORGANIZATION_OWNER', 'HOTEL_OWNER', 'HOTEL_MANAGER', 'HOTEL_STAFF')),
    scope_type VARCHAR(20) NOT NULL CHECK (scope_type IN ('ORGANIZATION', 'HOTEL')),
    -- HOTEL is a remote reference, not a cross-service DB foreign key.
    scope_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_assignment_membership_organization
        FOREIGN KEY (membership_id, organization_id)
        REFERENCES organization_memberships(id, organization_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT uq_membership_role_scope UNIQUE (membership_id, role, scope_type, scope_id),
    CONSTRAINT ck_assignment_organization_scope
        CHECK (scope_type <> 'ORGANIZATION' OR scope_id = organization_id),
    CONSTRAINT ck_assignment_owner_scope
        CHECK (role <> 'ORGANIZATION_OWNER' OR scope_type = 'ORGANIZATION')
);

CREATE INDEX idx_assignments_organization_scope ON role_assignments(organization_id, scope_type, scope_id);

create table if not exists banner
(
    id           bigint generated always as identity primary key,
    type         smallint    not null default 0,
    key          text        not null,
    link_url     text,
    sort_order   int         not null default 0,
    is_active    boolean     not null default true,
    created_at   timestamptz not null default current_timestamp,
    updated_at   timestamptz not null default current_timestamp
);

comment on table banner is 'Banner 图片';
comment on column banner.id is 'Banner ID';
comment on column banner.type is 'Banner 的位置: 0=首页';
comment on column banner.key is 'R2 对象 Key';
comment on column banner.link_url is '点击跳转地址，NULL=不跳转';
comment on column banner.sort_order is '轮播顺序，数值越小越靠前，同值按 ID 升序排列';
comment on column banner.is_active is '是否参与轮换';
comment on column banner.created_at is '创建时间';
comment on column banner.updated_at is '最后修改时间，由应用层更新';

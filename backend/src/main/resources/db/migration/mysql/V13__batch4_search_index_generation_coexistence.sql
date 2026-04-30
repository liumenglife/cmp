alter table ia_search_document drop primary key;

alter table ia_search_document add primary key (search_doc_id, rebuild_generation);

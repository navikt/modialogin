CREATE FUNCTION publish_event() RETURNS TRIGGER AS $$
    BEGIN
        PERFORM pg_notify('persistence_updates', row_to_json(NEW)::text);
        RETURN NEW;
    END
$$ LANGUAGE plpgsql;

CREATE TRIGGER publish_persistence_trigger
AFTER INSERT OR UPDATE ON persistence
FOR EACH ROW EXECUTE PROCEDURE publish_event();
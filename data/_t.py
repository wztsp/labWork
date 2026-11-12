import psycopg2
c = psycopg2.connect(host='localhost', port=5432, user='lis_user', password='lis_dev_2026', dbname='lis_db')
cur = c.cursor()
cur.execute("SELECT column_name FROM information_schema.columns WHERE table_name='lis_staff' ORDER BY ordinal_position")
print("lis_staff cols:", [r[0] for r in cur.fetchall()])
c.close()
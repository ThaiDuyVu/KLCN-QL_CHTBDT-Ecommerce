"""Only the disposable cloned database is writable by this harness."""
import uuid
from common import *
import psycopg

def prepare():
 s=load(PRIVATE/'state.json');assert s['container'].startswith('chatbot-post-upgrade-');ids={}
 with psycopg.connect(s['dsn']) as conn:
  owner=conn.execute("SELECT c.customer_id FROM customers c JOIN users u ON u.user_id=c.user_id WHERE u.username='seed.customer1'").fetchone()[0]
  vid,price=conn.execute("SELECT variant_id,price FROM product_variants WHERE sku='DEV-X15-BLK-256'").fetchone()
  for code,status,payment,age,warehouse in [('ORD-DIAG-DELIVERED','DELIVERED','PAID',1,s['warehouse_id']),('ORD-DIAG-FAILED','PENDING','FAILED',5,s['warehouse_id']),('ORD-DIAG-NOPAY','DELIVERED',None,10,s['warehouse_id']),('ORD-DIAG-NULLWAREHOUSE','PENDING','PENDING',15,None),('ORD-DIAG-EXPIRED','DELIVERED','PAID',800*24*60,s['warehouse_id'])]:
   oid=uuid.uuid4();ids[code]=str(oid)
   conn.execute("INSERT INTO orders(order_id,customer_id,warehouse_id,order_code,order_date,recipient_name,recipient_phone,shipping_address,subtotal,total_amount,status,note) VALUES(%s,%s,%s,%s,CURRENT_TIMESTAMP-make_interval(mins=>%s),%s,%s,%s,%s,%s,%s,%s)",(oid,owner,warehouse,code,age,'Khách kiểm thử tổng hợp','0000000000','Địa chỉ dữ liệu kiểm thử',price,price,status,'Disposable isolated diagnostic fixture'))
   if payment:
    conn.execute('INSERT INTO payments(order_id,payment_method,amount,status) VALUES(%s,%s,%s,%s)',(oid,'COD',price,payment))
   if code in ['ORD-DIAG-DELIVERED','ORD-DIAG-EXPIRED']:
    serial,item,warranty=[uuid.uuid4() for _ in range(3)]
    conn.execute("INSERT INTO serial_numbers(serial_id,variant_id,warehouse_id,serial_number,status) VALUES(%s,%s,%s,%s,'SOLD')",(serial,vid,s['warehouse_id'],'DIAG-'+uuid.uuid4().hex))
    conn.execute('INSERT INTO order_items(order_item_id,order_id,variant_id,serial_id,quantity,cost_price,unit_price,final_unit_price) VALUES(%s,%s,%s,%s,1,%s,%s,%s)',(item,oid,vid,serial,price,price,price))
    expired=code.endswith('EXPIRED')
    conn.execute("INSERT INTO warranties(warranty_id,serial_id,order_item_id,customer_id,start_date,end_date,status) VALUES(%s,%s,%s,%s,CURRENT_DATE-%s,CURRENT_DATE+%s,%s)",(warranty,serial,item,owner,800 if expired else 0,-435 if expired else 365,'EXPIRED' if expired else 'ACTIVE'))
    if not expired:
     conn.execute("INSERT INTO warranty_tickets(ticket_code,warranty_id,serial_id,customer_id,issue_description,status) VALUES('BH-DIAG-RECEIVED',%s,%s,%s,%s,'RECEIVED')",(warranty,serial,owner,'Lỗi giả lập phục vụ kiểm thử, không phải yêu cầu thực tế'))
  s['fixture_order_ids']=ids;dump(PRIVATE/'state.json',s)
 dump(OUT/'evidence/fixtures.json',{'scope':'DISPOSABLE_CLONED_DB_ONLY','order_codes':list(ids),'new_orders':5,'new_payments':4,'new_warranties':2,'new_tickets':1,'nullable_warehouse_fixture':'schema-valid legacy row, not checkout mutation','cleanup_policy':'drop whole disposable DB/container; source hash checked separately'})
 print('Isolated synthetic fixtures prepared')
if __name__=='__main__':prepare()

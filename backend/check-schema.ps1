$h2 = Get-Content "E:\code\crm\backend\src\test\resources\schema-h2.sql" -Raw
$migs = Get-ChildItem "E:\code\crm\backend\src\main\resources\db\migration" -Filter "V*.sql" | Sort-Object { [int]($_.Name -replace 'V(\d+)_.*','$1') }
$checked = @('custom_object','custom_object_record','integration_channel','currency_rate','product_price','field_permission','call_record','mail_account','mail_sync_record','sla_calendar_config','signature_record','landing_page','ticket_survey','email_unsubscribe','api_key','webhook_subscription','webhook_delivery','stage_action_template','sales_opportunity_action','product')
foreach ($t in $checked) {
  $inH2 = $h2 -match "CREATE TABLE $t "
  $inMig = $migs | Where-Object { (Get-Content $_.FullName -Raw) -match "CREATE TABLE ``$t``" }
  $st = if ($inH2) { 'H2-OK' } else { 'H2-MISSING' }
  $mt = if ($inMig) { $inMig.Name } else { 'MIG-MISSING' }
  Write-Host "$t  $st  $mt"
}

import { Tabs } from 'antd'
import EmailTemplatePage from './EmailTemplatePage'
import EmailCampaignPage from './EmailCampaignPage'

/** 邮件营销容器页（030）。 */
export default function EmailMarketingPage() {
  return (
    <Tabs
      defaultActiveKey="campaigns"
      items={[
        { key: 'campaigns', label: '邮件群发', children: <EmailCampaignPage /> },
        { key: 'templates', label: '邮件模板', children: <EmailTemplatePage /> },
      ]}
    />
  )
}

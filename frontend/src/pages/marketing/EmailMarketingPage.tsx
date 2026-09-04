import { useTranslation } from 'react-i18next'
import { Tabs } from 'antd'
import EmailTemplatePage from './EmailTemplatePage'
import EmailCampaignPage from './EmailCampaignPage'

/** 邮件营销容器页（030）。 */
export default function EmailMarketingPage() {
  const { t } = useTranslation()
  return (
    <Tabs
      defaultActiveKey="campaigns"
      items={[
        { key: 'campaigns', label: t('pages.marketing.emailMarketing.tabCampaigns'), children: <EmailCampaignPage /> },
        { key: 'templates', label: t('pages.marketing.emailMarketing.tabTemplates'), children: <EmailTemplatePage /> },
      ]}
    />
  )
}

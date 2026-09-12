import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { Button, Result } from 'antd'
import { HomeOutlined } from '@ant-design/icons'

/** 404 页面（布局优化）：未知路径友好提示，带返回首页入口。 */
export default function NotFoundPage() {
  const navigate = useNavigate()
  const { t } = useTranslation()
  return (
    <div style={{ display: 'flex', minHeight: '60vh', alignItems: 'center', justifyContent: 'center' }}>
      <Result
        status="404"
        title={t('pages.notFound.title')}
        subTitle={t('pages.notFound.subTitle')}
        extra={
          <Button type="primary" icon={<HomeOutlined />} onClick={() => navigate('/', { replace: true })}>
            {t('pages.notFound.btnHome')}
          </Button>
        }
      />
    </div>
  )
}

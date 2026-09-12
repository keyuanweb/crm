import { useTranslation } from 'react-i18next'
import { Tabs } from 'antd'
import TagListPage from './TagListPage'
import SegmentListPage from './SegmentListPage'

/** 标签与细分容器页（031）。 */
export default function TagSegmentPage() {
  const { t } = useTranslation()
  return (
    <Tabs
      defaultActiveKey="tags"
      items={[
        { key: 'tags', label: t('pages.tagSegment.tabTags'), children: <TagListPage /> },
        { key: 'segments', label: t('pages.tagSegment.tabSegments'), children: <SegmentListPage /> },
      ]}
    />
  )
}
